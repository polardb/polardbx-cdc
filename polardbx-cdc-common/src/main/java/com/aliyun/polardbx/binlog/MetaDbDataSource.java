/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.binlog.util.EnvPropMap;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.Validator;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.METADB_SCAN_INTERVAL_SECONDS;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * meta db datasource
 *
 * @author yudong
 **/
@Slf4j
public class MetaDbDataSource implements javax.sql.DataSource, PoolConfiguration {

    private final boolean useEncryptedPassword;

    private final String dnPasswordKey;

    private String metaDbUrl;

    private String metaDbUrlTemplate;

    private DataSource metaDbDataSource;

    private final PoolProperties poolProperties;

    private ExecutorService metaDbUrlScanService;

    private static final String METADB_URL_SCAN_SQL = "SHOW STORAGE";

    private static final String CN_URL_TEMPLATE = "jdbc:mysql://%s/__cdc__?useSSL=false";

    private boolean bootstrap = true;

    @Setter
    private boolean metaDbScanSwitch;

    public MetaDbDataSource(String dnPasswordKey, boolean useEncryptedPassword) {
        this(dnPasswordKey, useEncryptedPassword, new PoolProperties());
    }

    public MetaDbDataSource(String dnPasswordKey, boolean useEncryptedPassword, PoolProperties poolProperties) {
        this.dnPasswordKey = dnPasswordKey;
        this.useEncryptedPassword = useEncryptedPassword;
        this.poolProperties = poolProperties;
    }

    public void init() {
        // 使用配置文件中的配置
        this.metaDbDataSource = buildMetaDbDataSource();

        if (metaDbScanSwitch) {
            this.metaDbUrlTemplate = getMetaDbUrlTemplate(metaDbUrl);

            CnDataSourceFileCache.getInstance()
                .init(ConfigPropMap.getPropertyValue(ConfigKeys.LATEST_SERVER_ADDRESS_PERSIST_FILE));

            if (!isConnective()) {
                // env中的meta_url不可用
                // 尝试依赖latest server文件获得CN的连接串，连接CN执行show storage获得meta_url
                scan();
            } else {
                if (!isLeaderAndAvailable()) {
                    // meta_url不是leader，使用该url获得CN的连接, 连接CN执行show storage获得meta_url
                    writeLatestServer();
                    scan();
                }
            }

            bootstrap = false;
            this.metaDbUrlScanService = Executors.newSingleThreadExecutor
                (r -> {
                    Thread t = new Thread(r, "metadb-url-scanner");
                    t.setDaemon(true);
                    return t;
                });
            this.metaDbUrlScanService.submit(() -> {
                //等5s，spring初始化完毕
                sleep(5000);
                long lastCheckTime = System.currentTimeMillis();
                while (!Thread.interrupted()) {
                    if (System.currentTimeMillis() - lastCheckTime > scanInterval()) {
                        if (!isConnective() || !isLeaderAndAvailable()) {
                            scan();
                        }
                        lastCheckTime = System.currentTimeMillis();
                    } else {
                        sleep(1000);
                    }
                }
                log.info("metadb url scan service runs end.");
            });
        }
    }

    private void sleep(long mill) {
        try {
            CommonUtils.sleep(mill);
        } catch (InterruptedException e) {
        }
    }

    private long scanInterval() {
        try {
            return DynamicApplicationConfig.getLong(METADB_SCAN_INTERVAL_SECONDS) * 1000;
        } catch (Throwable t) {
            log.error("get scan interval error from dynamic application config!", t);
            try {
                return Long.parseLong(ConfigPropMap.getPropertyValue(METADB_SCAN_INTERVAL_SECONDS)) * 1000;
            } catch (Throwable tx) {
                log.error("get scan interval error from config prop map!", t);
                return 5000;
            }
        }
    }

    public void close() {
        metaDbUrlScanService.shutdownNow();
    }

    /**
     * 连接cn执行show storage获得meta db的ip:port
     */
    private synchronized void scan() {
        try (Connection conn = getCnConnection();
            Statement stmt = conn.createStatement();
            ResultSet resultSet = stmt.executeQuery(METADB_URL_SCAN_SQL)) {
            while (resultSet.next()) {
                String instKind = resultSet.getString("INST_KIND");
                if (!"META_DB".equals(instKind)) {
                    continue;
                }
                String ipAndPort = resultSet.getString("LEADER_NODE");
                if (StringUtils.isEmpty(ipAndPort)) {
                    log.error("meta db url is empty!");
                    return;
                }
                String newUrl = String.format(this.metaDbUrlTemplate, ipAndPort);
                if (!newUrl.equals(this.getUrl())) {
                    log.info("meta db url has changed, old url:{}, new url:{}", this.metaDbUrl, newUrl);
                    haWitch(newUrl);
                }
            }
        } catch (Throwable e) {
            // 启动过程中调用scan时抛出异常，说明env中的meta url不可用
            // 并且latest_server文件中的所有cn连接不可用，系统初始化必然失败
            if (bootstrap) {
                throw new PolardbxException(e);
            }

            // catch exception, 防止扫描线程退出
            log.error("scan meta db url error", e);
        }

        bootstrap = false;
    }

    private void haWitch(String newUrl) {
        metaDbDataSource.close();
        setUrl(newUrl);
        metaDbDataSource = buildMetaDbDataSource();
    }

    private boolean isConnective() {
        try (Connection conn = metaDbDataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Throwable e) {
            log.info("meta url not connective", e);
            return false;
        }
    }

    public boolean isLeaderAndAvailable() {
        if (Boolean.parseBoolean(
            ConfigPropMap.getPropertyValue(ConfigKeys.BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE))) {
            try {
                return SQLUtils.isLeaderByDdl(metaDbDataSource);
            } catch (Throwable e) {
                log.info("this is not leader or leader not available, url:{}", metaDbDataSource.getUrl(), e);
                return false;
            }
        } else {
            try {
                return SQLUtils.isLeaderBySqlQuery(metaDbDataSource);
            } catch (Throwable e) {
                log.info("this is not leader or leader not available, url:{}", metaDbDataSource.getUrl(), e);
                return false;
            }
        }
    }

    private void writeLatestServer() {
        try (Connection conn = metaDbDataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement("select ip, port from server_info");
            ResultSet resultSet = ps.executeQuery()) {
            Set<String> serverAddrs = new HashSet<>();
            while (resultSet.next()) {
                String addr = resultSet.getString("ip") + ":" + resultSet.getString("port");
                serverAddrs.add(addr);
            }
            log.info("latest server addr:{}", serverAddrs);
            CnDataSourceFileCache.CnDataSourceStruct struct = new CnDataSourceFileCache.CnDataSourceStruct(serverAddrs,
                EnvPropMap.getPropertyValue(ConfigKeys.POLARX_USERNAME),
                EnvPropMap.getPropertyValue(ConfigKeys.POLARX_PASSWORD),
                EnvPropMap.getPropertyValue(ConfigKeys.POLARX_INST_ID));
            CnDataSourceFileCache.getInstance().write(struct);
        } catch (Exception e) {
            log.info("query server info error", e);
            throw new PolardbxException(e);
        }
    }

    /**
     * 首次启动的时候 CnDataSource的初始化依赖MetaDataSource，所以metaDataSource需要从文件中获得CN的连接
     * 启动完成之后CnDataSource维持了CN datasource的一个缓存，所以MetaDataSource可以使用CnDataSource
     */
    private Connection getCnConnection() throws SQLException {
        if (bootstrap) {
            CnDataSourceFileCache.CnDataSourceStruct struct = CnDataSourceFileCache.getInstance().read();
            if (struct == null || struct.getUrls().isEmpty()) {
                throw new PolardbxException("meta db file is empty, cannot build meta db connection!");
            }

            for (String address : struct.getUrls()) {
                String url = String.format(CN_URL_TEMPLATE, address);
                log.info("try connect cn with url:{}", url);
                try {
                    return DriverManager.getConnection(url, struct.getUsername(), struct.getPassword());
                } catch (SQLException e) {
                    log.info("cn url:{} is not available, will try another one", url, e);
                }
            }
            throw new PolardbxException("all cn url is not available!");
        } else {
            JdbcTemplate cnTemplate = getObject("polarxJdbcTemplate");
            return Objects.requireNonNull(cnTemplate.getDataSource()).getConnection();
        }
    }

    @Override
    public Connection getConnection() {
        try {
            return metaDbDataSource.getConnection();
        } catch (Throwable ex) {
            throw new PolardbxException("get meta db conn error!", ex);
        }
    }

    @Override
    public Connection getConnection(String username, String password) {
        try {
            return metaDbDataSource.getConnection(username, password);
        } catch (Throwable ex) {
            throw new PolardbxException("get meta db conn error!", ex);
        }
    }

    private DataSource buildMetaDbDataSource() {
        return new DataSource(poolProperties);
    }

    private String getMetaDbUrlTemplate(String metaDbUrl) {
        String regex = "//(.*?):(.*?)/";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(metaDbUrl);
        return matcher.replaceFirst("//%s/");
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int seconds) {
        poolProperties.setMaxWait(seconds * 1000);
    }

    @Override
    public int getLoginTimeout() {
        return poolProperties.getMaxWait() / 1000;
    }

    @Override
    public Logger getParentLogger() {
        return null;
    }

    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        this.poolProperties.setAbandonWhenPercentageFull(percentage);
    }

    @Override
    public int getAbandonWhenPercentageFull() {
        return poolProperties.getAbandonWhenPercentageFull();
    }

    @Override
    public boolean isFairQueue() {
        return this.poolProperties.isFairQueue();
    }

    @Override
    public void setFairQueue(boolean fairQueue) {
        this.poolProperties.setFairQueue(fairQueue);
    }

    @Override
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return this.poolProperties.isAccessToUnderlyingConnectionAllowed();
    }

    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean b) {
        this.poolProperties.setAccessToUnderlyingConnectionAllowed(b);
    }

    @Override
    public String getConnectionProperties() {
        return this.poolProperties.getConnectionProperties();
    }

    @Override
    public void setConnectionProperties(String properties) {
        try {
            Properties prop = PoolProperties.getProperties(properties, null);
            for (Object o : prop.keySet()) {
                String key = (String) o;
                String value = prop.getProperty(key);
                this.poolProperties.getDbProperties().setProperty(key, value);
            }
        } catch (Exception e) {
            log.error("unable to parse connection properties:{}", properties);
            throw new PolardbxException(e);
        }
    }

    @Override
    public Properties getDbProperties() {
        return this.poolProperties.getDbProperties();
    }

    @Override
    public void setDbProperties(Properties properties) {
        this.poolProperties.setDbProperties(properties);
    }

    @Override
    public Boolean isDefaultAutoCommit() {
        return this.poolProperties.isDefaultAutoCommit();
    }

    @Override
    public Boolean getDefaultAutoCommit() {
        return this.poolProperties.getDefaultAutoCommit();
    }

    @Override
    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        this.poolProperties.setDefaultAutoCommit(defaultAutoCommit);
    }

    @Override
    public String getDefaultCatalog() {
        return this.poolProperties.getDefaultCatalog();
    }

    @Override
    public void setDefaultCatalog(String defaultCatalog) {
        this.poolProperties.setDefaultCatalog(defaultCatalog);
    }

    @Override
    public Boolean isDefaultReadOnly() {
        return this.poolProperties.isDefaultReadOnly();
    }

    @Override
    public Boolean getDefaultReadOnly() {
        return this.poolProperties.getDefaultReadOnly();
    }

    @Override
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        this.poolProperties.setDefaultReadOnly(defaultReadOnly);
    }

    @Override
    public int getDefaultTransactionIsolation() {
        return this.poolProperties.getDefaultTransactionIsolation();
    }

    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.poolProperties.setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    @Override
    public String getDriverClassName() {
        return this.poolProperties.getDriverClassName();
    }

    @Override
    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }

    @Override
    public int getInitialSize() {
        return this.poolProperties.getInitialSize();
    }

    @Override
    public void setInitialSize(int initialSize) {
        this.poolProperties.setInitialSize(initialSize);
    }

    @Override
    public boolean isLogAbandoned() {
        return this.poolProperties.isLogAbandoned();
    }

    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }

    @Override
    public int getMaxActive() {
        return this.poolProperties.getMaxActive();
    }

    @Override
    public void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
    }

    @Override
    public int getMaxIdle() {
        return this.poolProperties.getMaxIdle();
    }

    @Override
    public void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
    }

    @Override
    public int getMaxWait() {
        return poolProperties.getMaxWait();
    }

    @Override
    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }

    @Override
    public int getMinEvictableIdleTimeMillis() {
        return this.poolProperties.getMinEvictableIdleTimeMillis();
    }

    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);

    }

    @Override
    public int getMinIdle() {
        return this.poolProperties.getMinIdle();
    }

    @Override
    public void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
    }

    @Override
    public String getName() {
        return this.poolProperties.getName();
    }

    @Override
    public void setName(String name) {
        this.poolProperties.setName(name);
    }

    @Override
    public int getNumTestsPerEvictionRun() {
        return this.poolProperties.getNumTestsPerEvictionRun();
    }

    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }

    @Override
    public String getPassword() {
        return poolProperties.getPassword();
    }

    @Override
    public void setPassword(String password) {
        if (useEncryptedPassword) {
            password = PasswdUtil.decryptBase64(password, dnPasswordKey);
        }
        this.poolProperties.setPassword(password);
        this.poolProperties.getDbProperties().setProperty("password", this.poolProperties.getPassword());
    }

    @Override
    public String getPoolName() {
        return this.poolProperties.getPoolName();
    }

    @Override
    public String getUsername() {
        return this.poolProperties.getUsername();
    }

    @Override
    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
        this.poolProperties.getDbProperties().setProperty("user", this.poolProperties.getUsername());
    }

    @Override
    public boolean isRemoveAbandoned() {
        return this.poolProperties.isRemoveAbandoned();
    }

    @Override
    public void setRemoveAbandoned(boolean b) {
        this.poolProperties.setRemoveAbandoned(b);
    }

    @Override
    public void setRemoveAbandonedTimeout(int i) {
        this.poolProperties.setRemoveAbandonedTimeout(i);
    }

    @Override
    public int getRemoveAbandonedTimeout() {
        return this.poolProperties.getRemoveAbandonedTimeout();
    }

    @Override
    public boolean isTestOnBorrow() {
        return this.poolProperties.isTestOnBorrow();
    }

    @Override
    public void setTestOnBorrow(boolean b) {
        this.poolProperties.setTestOnBorrow(b);
    }

    @Override
    public boolean isTestOnReturn() {
        return this.poolProperties.isTestOnReturn();
    }

    @Override
    public void setTestOnReturn(boolean b) {
        this.poolProperties.setTestOnReturn(b);
    }

    @Override
    public boolean isTestWhileIdle() {
        return this.poolProperties.isTestWhileIdle();
    }

    @Override
    public void setTestWhileIdle(boolean b) {
        this.poolProperties.setTestWhileIdle(b);
    }

    @Override
    public int getTimeBetweenEvictionRunsMillis() {
        return this.poolProperties.getTimeBetweenEvictionRunsMillis();
    }

    @Override
    public void setTimeBetweenEvictionRunsMillis(int i) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(i);
    }

    @Override
    public String getUrl() {
        return this.metaDbUrl;
    }

    @Override
    public void setUrl(String s) {
        this.metaDbUrl = s;
        this.poolProperties.setUrl(s);
    }

    @Override
    public String getValidationQuery() {
        return this.poolProperties.getValidationQuery();
    }

    @Override
    public void setValidationQuery(String s) {
        this.poolProperties.setValidationQuery(s);
    }

    @Override
    public int getValidationQueryTimeout() {
        return this.poolProperties.getValidationQueryTimeout();
    }

    @Override
    public void setValidationQueryTimeout(int i) {
        this.poolProperties.setValidationQueryTimeout(i);
    }

    @Override
    public String getValidatorClassName() {
        return this.poolProperties.getValidatorClassName();
    }

    @Override
    public void setValidatorClassName(String s) {
        this.poolProperties.setValidatorClassName(s);
    }

    @Override
    public Validator getValidator() {
        return this.poolProperties.getValidator();
    }

    @Override
    public void setValidator(Validator validator) {
        this.poolProperties.setValidator(validator);
    }

    @Override
    public long getValidationInterval() {
        return this.poolProperties.getValidationInterval();
    }

    @Override
    public void setValidationInterval(long l) {
        this.poolProperties.setValidationInterval(l);
    }

    @Override
    public String getInitSQL() {
        return this.poolProperties.getInitSQL();
    }

    @Override
    public void setInitSQL(String s) {
        this.poolProperties.setInitSQL(s);
    }

    @Override
    public boolean isTestOnConnect() {
        return this.poolProperties.isTestOnConnect();
    }

    @Override
    public void setTestOnConnect(boolean b) {
        this.poolProperties.setTestOnConnect(b);
    }

    @Override
    public String getJdbcInterceptors() {
        return this.poolProperties.getJdbcInterceptors();
    }

    @Override
    public void setJdbcInterceptors(String s) {
        this.poolProperties.setJdbcInterceptors(s);
    }

    @Override
    public PoolProperties.InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return new PoolProperties.InterceptorDefinition[0];
    }

    @Override
    public boolean isJmxEnabled() {
        return this.poolProperties.isJmxEnabled();
    }

    @Override
    public void setJmxEnabled(boolean b) {
        this.poolProperties.setJmxEnabled(b);
    }

    @Override
    public boolean isPoolSweeperEnabled() {
        return this.poolProperties.isPoolSweeperEnabled();
    }

    @Override
    public boolean isUseEquals() {
        return this.poolProperties.isUseEquals();
    }

    @Override
    public void setUseEquals(boolean b) {
        this.poolProperties.setUseEquals(b);
    }

    @Override
    public long getMaxAge() {
        return this.poolProperties.getMaxAge();
    }

    @Override
    public void setMaxAge(long l) {
        this.poolProperties.setMaxAge(l);
    }

    @Override
    public boolean getUseLock() {
        return this.poolProperties.getUseLock();
    }

    @Override
    public void setUseLock(boolean b) {
        this.poolProperties.setUseLock(b);
    }

    @Override
    public void setSuspectTimeout(int i) {
        this.poolProperties.setSuspectTimeout(i);
    }

    @Override
    public int getSuspectTimeout() {
        return this.poolProperties.getSuspectTimeout();
    }

    @Override
    public void setDataSource(Object o) {
        this.poolProperties.setDataSource(o);
    }

    @Override
    public Object getDataSource() {
        return this.poolProperties.getDataSource();
    }

    @Override
    public void setDataSourceJNDI(String s) {
        this.poolProperties.setDataSourceJNDI(s);
    }

    @Override
    public String getDataSourceJNDI() {
        return this.poolProperties.getDataSourceJNDI();
    }

    @Override
    public boolean isAlternateUsernameAllowed() {
        return this.poolProperties.isAlternateUsernameAllowed();
    }

    @Override
    public void setAlternateUsernameAllowed(boolean b) {
        this.poolProperties.setAlternateUsernameAllowed(b);
    }

    @Override
    public void setCommitOnReturn(boolean b) {
        this.poolProperties.setCommitOnReturn(b);
    }

    @Override
    public boolean getCommitOnReturn() {
        return this.poolProperties.getCommitOnReturn();
    }

    @Override
    public void setRollbackOnReturn(boolean b) {
        this.poolProperties.setRollbackOnReturn(b);
    }

    @Override
    public boolean getRollbackOnReturn() {
        return this.poolProperties.getRollbackOnReturn();
    }

    @Override
    public void setUseDisposableConnectionFacade(boolean b) {
        this.poolProperties.setUseDisposableConnectionFacade(b);
    }

    @Override
    public boolean getUseDisposableConnectionFacade() {
        return this.poolProperties.getUseDisposableConnectionFacade();
    }

    @Override
    public void setLogValidationErrors(boolean b) {
        this.poolProperties.setLogValidationErrors(b);
    }

    @Override
    public boolean getLogValidationErrors() {
        return this.poolProperties.getLogValidationErrors();
    }

    @Override
    public boolean getPropagateInterruptState() {
        return this.poolProperties.getPropagateInterruptState();
    }

    @Override
    public void setPropagateInterruptState(boolean b) {
        this.poolProperties.setPropagateInterruptState(b);
    }

    @Override
    public void setIgnoreExceptionOnPreLoad(boolean b) {
        this.poolProperties.setIgnoreExceptionOnPreLoad(b);
    }

    @Override
    public boolean isIgnoreExceptionOnPreLoad() {
        return this.poolProperties.isIgnoreExceptionOnPreLoad();
    }

    @Override
    public void setUseStatementFacade(boolean b) {
        this.poolProperties.setUseStatementFacade(b);
    }

    @Override
    public boolean getUseStatementFacade() {
        return this.poolProperties.getUseStatementFacade();
    }
}
