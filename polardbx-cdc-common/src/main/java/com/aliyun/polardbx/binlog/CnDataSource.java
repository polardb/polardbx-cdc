/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.DATASOURCE_CHECK_VALID_TIMEOUT_SEC;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 **/
public class CnDataSource implements PoolConfiguration, javax.sql.DataSource, javax.sql.ConnectionPoolDataSource {
    private static final Logger logger = LoggerFactory.getLogger(CnDataSource.class);
    private static final int SERVER_CHECK_INTERVAL = 1000;

    private final boolean flushAddressSwitch;
    /**
     * 默认30秒刷一次 latest server ip
     */
    private final int serverAddressFlushInterval;

    private int fileCacheFlushTicker = 0;

    protected volatile PoolProperties poolProperties;
    protected ReentrantReadWriteLock readWriteLock;
    protected AtomicLong seed;
    protected String urlTemplate;
    protected List<String> nestedAddresses;
    protected LoadingCache<String, DataSource> nestedDataSources;
    protected volatile DataSource proxyDataSource;
    protected ScheduledExecutorService scheduledExecutorService;
    protected String dnPasswordKey;
    protected boolean useEncryptedPassword;

    public CnDataSource(String dnPasswordKey, boolean useEncryptedPassword) {
        this(dnPasswordKey, useEncryptedPassword, new PoolProperties());
    }

    public CnDataSource(String dnPasswordKey, boolean useEncryptedPassword, PoolProperties poolProperties) {
        if (poolProperties == null) {
            throw new NullPointerException("PoolConfiguration cannot be null.");
        }

        this.dnPasswordKey = dnPasswordKey;
        this.useEncryptedPassword = useEncryptedPassword;
        this.poolProperties = poolProperties;
        this.readWriteLock = new ReentrantReadWriteLock();
        this.seed = new AtomicLong();
        this.serverAddressFlushInterval =
            Integer.parseInt(ConfigPropMap.getPropertyValue(ConfigKeys.LATEST_SERVER_ADDRESS_FLUSH_INTERVAL));
        this.flushAddressSwitch =
            Boolean.parseBoolean(ConfigPropMap.getPropertyValue(ConfigKeys.METADB_SCAN_SWITCH));
        this.nestedAddresses = new ArrayList<>();
        this.nestedDataSources = CacheBuilder.newBuilder()
            .removalListener(
                (RemovalListener<String, DataSource>) notification -> {
                    DataSource ds = notification.getValue();
                    try {
                        ds.close();
                        logger.info("successfully close datasource for " + notification.getKey());
                    } catch (Exception e) {
                        logger.error("close datasource failed for " + notification.getKey());
                    }
                })
            .build(new CacheLoader<String, DataSource>() {
                @Override
                @ParametersAreNonnullByDefault
                public DataSource load(String address) {
                    PoolProperties newPoolProperties = new PoolProperties();
                    BeanUtils.copyProperties(poolProperties, newPoolProperties);
                    newPoolProperties.setUrl(String.format(urlTemplate, address));
                    return new DataSource(newPoolProperties);
                }
            });
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    @SuppressWarnings("unused") // Has to match signature in DataSource
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // we are not a wrapper of anything
        return false;
    }

    @SuppressWarnings("unused") // Has to match signature in DataSource
    public <T> T unwrap(Class<T> iface) throws SQLException {
        //we can't unwrap anything
        return null;
    }

    public void init() throws IOException {
        if (StringUtils.isNotBlank(poolProperties.getUrl())) {
            proxyDataSource = new DataSource(poolProperties);
        } else {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
                Thread thread = new Thread(r, "server-node-scanner");
                thread.setDaemon(true);
                return thread;
            });
            scheduledExecutorService
                .scheduleAtFixedRate(this::scan, SERVER_CHECK_INTERVAL, SERVER_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void scan() {
        try {
            Set<String> latestServers = getLatestServerAddress();
            Set<String> holdingServers = Sets.newHashSet(nestedAddresses);
            Set<String> toBeAddedServers =
                latestServers.stream().filter(s -> !holdingServers.contains(s)).collect(Collectors.toSet());
            Set<String> toBeRemovedServers =
                holdingServers.stream().filter(s -> !latestServers.contains(s)).collect(Collectors.toSet());
            int timeout = DynamicApplicationConfig.getInt(DATASOURCE_CHECK_VALID_TIMEOUT_SEC);
            Set<String> invalidHoldingServers = holdingServers.stream().filter(s -> {
                try (Connection conn = nestedDataSources.getUnchecked(s).getConnection()) {
                    if (conn.isValid(timeout)) {
                        return false;
                    } else {
                        logger.warn("detected abnormal server node with address {}", s);
                    }
                } catch (Throwable t) {
                    logger.warn("detected abnormal server node with address {}", s, t);
                }
                return true;
            }).collect(Collectors.toSet());
            toBeRemovedServers.addAll(invalidHoldingServers);

            if (!toBeAddedServers.isEmpty()) {
                onServerNodeAdd(toBeAddedServers);
            }
            if (!toBeRemovedServers.isEmpty()) {
                onServerNodeRemove(toBeRemovedServers);
            }

            // persist latest server
            if (flushAddressSwitch) {
                fileCacheFlushTicker++;
                if (!toBeAddedServers.isEmpty() || !toBeRemovedServers.isEmpty()
                    || fileCacheFlushTicker == serverAddressFlushInterval) {
                    fileCacheFlushTicker = 0;
                    CnDataSourceFileCache.CnDataSourceStruct struct =
                        new CnDataSourceFileCache.CnDataSourceStruct(holdingServers,
                            DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME),
                            DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD),
                            DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
                    CnDataSourceFileCache.getInstance().write(struct);
                }
            }
        } catch (Throwable e) {
            logger.error("something goes wrong in server node scan!", e);
        }
    }

    /**
     * instType: 0:master, 1:read without htap, 2:read with htap
     * status: 0: ready, 1: not_ready, 2: deleting
     */
    private Set<String> getLatestServerAddress() {
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        return serverInfoMapper.select(c -> c.where(instType, isEqualTo(0)).and(status, isEqualTo(0))).stream()
            .map(s -> String.format("%s:%s", s.getIp(), s.getPort())).collect(Collectors.toSet());
    }

    private void onServerNodeAdd(Set<String> toBeAddedServers) {
        int timeout = DynamicApplicationConfig.getInt(DATASOURCE_CHECK_VALID_TIMEOUT_SEC);
        Set<String> validServers = toBeAddedServers.stream().filter(s -> {
            try (Connection conn = nestedDataSources.getUnchecked(s).getConnection()) {
                if (conn.isValid(timeout)) {
                    return true;
                } else {
                    logger.warn("Server node {} is not ready yet, will retry later.", s);
                }
            } catch (Throwable t) {
                logger.warn("Server node {} is not ready yet, will retry later.", s, t);
            }
            nestedDataSources.invalidate(s);
            return false;
        }).collect(Collectors.toSet());

        if (!validServers.isEmpty()) {
            try {
                readWriteLock.writeLock().lock();
                nestedAddresses.addAll(validServers);
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }
    }

    private void onServerNodeRemove(Set<String> toBeRemovedServerInfoList) {
        try {
            readWriteLock.writeLock().lock();
            nestedAddresses.removeAll(toBeRemovedServerInfoList);
            toBeRemovedServerInfoList.forEach(s -> nestedDataSources.invalidate(s));
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     *
     * @param username The user name
     * @param password The password
     * @return the connection
     * @throws SQLException Connection error
     */
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnectionInternal(username, password);
    }

    public PoolConfiguration getPoolProperties() {
        return poolProperties;
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     *
     * @return the connection
     * @throws SQLException Connection error
     */
    public Connection getConnection() throws SQLException {
        return getConnectionInternal(null, null);
    }

    private Connection getConnectionInternal(String username, String password) throws SQLException {
        if (proxyDataSource != null) {
            return username == null ? proxyDataSource.getConnection() :
                proxyDataSource.getConnection(username, password);
        } else {
            try {
                readWriteLock.readLock().lock();

                if (nestedAddresses.isEmpty()) {
                    throw new PolardbxException("no server node is ready, please retry later.");
                }

                int index = (int) seed.incrementAndGet() % nestedAddresses.size();
                String key = nestedAddresses.get(index);
                return username == null ? nestedDataSources.getUnchecked(key).getConnection() :
                    nestedDataSources.getUnchecked(key).getConnection(username, password);
            } finally {
                readWriteLock.readLock().unlock();
            }
        }
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     *
     * @return the connection
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection() throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     *
     * @param username unused
     * @param password unused
     * @return the connection
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection(String username,
                                                          String password) throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    public void close() {
        close(false);
    }

    public void close(boolean all) {
        try {
            readWriteLock.writeLock().lock();
            nestedAddresses.clear();
            nestedDataSources.invalidateAll();
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (Exception x) {
            logger.warn("Error during connection pool closure.", x);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return super.toString() + "{" + getPoolProperties() + "}";
    }


    /*-----------------------------------------------------------------------*/
//      PROPERTIES WHEN NOT USED WITH FACTORY
    /*------------------------------------------------------------------------*/

    /**
     * {@inheritDoc}
     */

    @Override
    public String getPoolName() {
        return this.poolProperties.getPoolName();
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getParentLogger}
     *
     * @return no return value
     * @throws SQLFeatureNotSupportedException Unsupported
     */
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLogWriter}
     *
     * @return null
     * @throws SQLException No exception
     */
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#setLogWriter(PrintWriter)}
     *
     * @param out Ignored
     * @throws SQLException No exception
     */
    public void setLogWriter(PrintWriter out) throws SQLException {
        // NOOP
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLoginTimeout}
     *
     * @return the timeout
     */
    public int getLoginTimeout() {
        if (poolProperties == null) {
            return 0;
        } else {
            return poolProperties.getMaxWait() / 1000;
        }
    }

    /**
     * {@link javax.sql.DataSource#setLoginTimeout(int)}
     *
     * @param i The timeout value
     */
    public void setLoginTimeout(int i) {
        if (poolProperties == null) {
            return;
        } else {
            poolProperties.setMaxWait(1000 * i);
        }

    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getSuspectTimeout() {
        return getPoolProperties().getSuspectTimeout();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setSuspectTimeout(int seconds) {
        getPoolProperties().setSuspectTimeout(seconds);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getConnectionProperties() {
        return getPoolProperties().getConnectionProperties();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setConnectionProperties(String properties) {
        try {
            java.util.Properties prop = PoolProperties.getProperties(properties, null);
            Iterator<?> i = prop.keySet().iterator();
            while (i.hasNext()) {
                String key = (String) i.next();
                String value = prop.getProperty(key);
                getPoolProperties().getDbProperties().setProperty(key, value);
            }

        } catch (Exception x) {
            logger.error("Unable to parse connection properties.", x);
            throw new RuntimeException(x);
        }
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Properties getDbProperties() {
        return getPoolProperties().getDbProperties();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDbProperties(Properties dbProperties) {
        getPoolProperties().setDbProperties(dbProperties);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getDefaultCatalog() {
        return getPoolProperties().getDefaultCatalog();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDefaultCatalog(String catalog) {
        this.getPoolProperties().setDefaultCatalog(catalog);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getDefaultTransactionIsolation() {
        return getPoolProperties().getDefaultTransactionIsolation();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getDriverClassName() {
        return getPoolProperties().getDriverClassName();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getInitialSize() {
        return getPoolProperties().getInitialSize();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setInitialSize(int initialSize) {
        this.poolProperties.setInitialSize(initialSize);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getInitSQL() {
        return getPoolProperties().getInitSQL();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setInitSQL(String initSQL) {
        this.poolProperties.setInitSQL(initSQL);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getJdbcInterceptors() {
        return getPoolProperties().getJdbcInterceptors();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setJdbcInterceptors(String interceptors) {
        this.getPoolProperties().setJdbcInterceptors(interceptors);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getMaxActive() {
        return getPoolProperties().getMaxActive();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getMaxIdle() {
        return getPoolProperties().getMaxIdle();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getMaxWait() {
        return getPoolProperties().getMaxWait();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getMinEvictableIdleTimeMillis() {
        return getPoolProperties().getMinEvictableIdleTimeMillis();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getMinIdle() {
        return getPoolProperties().getMinIdle();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public long getMaxAge() {
        return getPoolProperties().getMaxAge();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setMaxAge(long maxAge) {
        getPoolProperties().setMaxAge(maxAge);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getName() {
        return getPoolProperties().getName();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getNumTestsPerEvictionRun() {
        return getPoolProperties().getNumTestsPerEvictionRun();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }

    /**
     * @return DOES NOT RETURN THE PASSWORD, IT WOULD SHOW UP IN JMX
     */
    @Override
    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }

    //=========================================================
    //  PROPERTIES / CONFIGURATION
    //=========================================================

    /**
     * {@inheritDoc}
     */

    @Override
    public void setPassword(String password) {
        if (useEncryptedPassword) {
            password = PasswdUtil.decryptBase64(password, dnPasswordKey);
        }
        this.poolProperties.setPassword(password);
        this.poolProperties.getDbProperties().setProperty("password", this.poolProperties.getPassword());
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getRemoveAbandonedTimeout() {
        return getPoolProperties().getRemoveAbandonedTimeout();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.poolProperties.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getTimeBetweenEvictionRunsMillis() {
        return getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getUrl() {
        return getPoolProperties().getUrl();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setUrl(String url) {
        this.poolProperties.setUrl(url);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getUsername() {
        return getPoolProperties().getUsername();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
        this.poolProperties.getDbProperties().setProperty("user", getPoolProperties().getUsername());
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public long getValidationInterval() {
        return getPoolProperties().getValidationInterval();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setValidationInterval(long validationInterval) {
        this.poolProperties.setValidationInterval(validationInterval);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getValidationQuery() {
        return getPoolProperties().getValidationQuery();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setValidationQuery(String validationQuery) {
        this.poolProperties.setValidationQuery(validationQuery);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getValidationQueryTimeout() {
        return getPoolProperties().getValidationQueryTimeout();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.poolProperties.setValidationQueryTimeout(validationQueryTimeout);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public String getValidatorClassName() {
        return getPoolProperties().getValidatorClassName();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setValidatorClassName(String className) {
        this.poolProperties.setValidatorClassName(className);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Validator getValidator() {
        return getPoolProperties().getValidator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValidator(Validator validator) {
        getPoolProperties().setValidator(validator);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        getPoolProperties().setAccessToUnderlyingConnectionAllowed(accessToUnderlyingConnectionAllowed);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Boolean isDefaultAutoCommit() {
        return getPoolProperties().isDefaultAutoCommit();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Boolean isDefaultReadOnly() {
        return getPoolProperties().isDefaultReadOnly();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isLogAbandoned() {
        return getPoolProperties().isLogAbandoned();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isPoolSweeperEnabled() {
        return getPoolProperties().isPoolSweeperEnabled();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isRemoveAbandoned() {
        return getPoolProperties().isRemoveAbandoned();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.poolProperties.setRemoveAbandoned(removeAbandoned);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public int getAbandonWhenPercentageFull() {
        return getPoolProperties().getAbandonWhenPercentageFull();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        getPoolProperties().setAbandonWhenPercentageFull(percentage);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isTestOnBorrow() {
        return getPoolProperties().isTestOnBorrow();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.poolProperties.setTestOnBorrow(testOnBorrow);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isTestOnConnect() {
        return getPoolProperties().isTestOnConnect();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        this.poolProperties.setTestOnConnect(testOnConnect);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isTestOnReturn() {
        return getPoolProperties().isTestOnReturn();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setTestOnReturn(boolean testOnReturn) {
        this.poolProperties.setTestOnReturn(testOnReturn);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isTestWhileIdle() {
        return getPoolProperties().isTestWhileIdle();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setTestWhileIdle(boolean testWhileIdle) {
        this.poolProperties.setTestWhileIdle(testWhileIdle);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Boolean getDefaultAutoCommit() {
        return getPoolProperties().getDefaultAutoCommit();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDefaultAutoCommit(Boolean autocommit) {
        this.getPoolProperties().setDefaultAutoCommit(autocommit);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public Boolean getDefaultReadOnly() {
        return getPoolProperties().getDefaultReadOnly();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        getPoolProperties().setDefaultReadOnly(defaultReadOnly);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public PoolProperties.InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return getPoolProperties().getJdbcInterceptorsAsArray();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean getUseLock() {
        return getPoolProperties().getUseLock();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setUseLock(boolean useLock) {
        this.getPoolProperties().setUseLock(useLock);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isFairQueue() {
        return getPoolProperties().isFairQueue();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setFairQueue(boolean fairQueue) {
        this.getPoolProperties().setFairQueue(fairQueue);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isJmxEnabled() {
        return getPoolProperties().isJmxEnabled();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setJmxEnabled(boolean enabled) {
        this.getPoolProperties().setJmxEnabled(enabled);
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean isUseEquals() {
        return getPoolProperties().isUseEquals();
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public void setUseEquals(boolean useEquals) {
        this.getPoolProperties().setUseEquals(useEquals);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getDataSource() {
        return getPoolProperties().getDataSource();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDataSource(Object ds) {
        getPoolProperties().setDataSource(ds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDataSourceJNDI() {
        return getPoolProperties().getDataSourceJNDI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDataSourceJNDI(String jndiDS) {
        getPoolProperties().setDataSourceJNDI(jndiDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlternateUsernameAllowed() {
        return getPoolProperties().isAlternateUsernameAllowed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAlternateUsernameAllowed(boolean alternateUsernameAllowed) {
        getPoolProperties().setAlternateUsernameAllowed(alternateUsernameAllowed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getCommitOnReturn() {
        return getPoolProperties().getCommitOnReturn();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommitOnReturn(boolean commitOnReturn) {
        getPoolProperties().setCommitOnReturn(commitOnReturn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getRollbackOnReturn() {
        return getPoolProperties().getRollbackOnReturn();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRollbackOnReturn(boolean rollbackOnReturn) {
        getPoolProperties().setRollbackOnReturn(rollbackOnReturn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseDisposableConnectionFacade() {
        return getPoolProperties().getUseDisposableConnectionFacade();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        getPoolProperties().setUseDisposableConnectionFacade(useDisposableConnectionFacade);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getLogValidationErrors() {
        return getPoolProperties().getLogValidationErrors();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogValidationErrors(boolean logValidationErrors) {
        getPoolProperties().setLogValidationErrors(logValidationErrors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getPropagateInterruptState() {
        return getPoolProperties().getPropagateInterruptState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPropagateInterruptState(boolean propagateInterruptState) {
        getPoolProperties().setPropagateInterruptState(propagateInterruptState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIgnoreExceptionOnPreLoad() {
        return getPoolProperties().isIgnoreExceptionOnPreLoad();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreExceptionOnPreLoad(boolean ignoreExceptionOnPreLoad) {
        getPoolProperties().setIgnoreExceptionOnPreLoad(ignoreExceptionOnPreLoad);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseStatementFacade() {
        return getPoolProperties().getUseStatementFacade();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseStatementFacade(boolean useStatementFacade) {
        getPoolProperties().setUseStatementFacade(useStatementFacade);
    }
}
