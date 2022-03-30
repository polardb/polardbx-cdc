/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.rpl.common;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;
import com.alibaba.druid.pool.vendor.MySqlValidConnectionChecker;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.PolarxCNodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by jiyue
 **/
public class DruidDataSourceWrapper extends DruidDataSource
    implements javax.sql.DataSource, javax.sql.ConnectionPoolDataSource {
    private static final Logger logger = LoggerFactory.getLogger(com.aliyun.polardbx.binlog.DataSourceWrapper.class);
    private static final int SERVER_CHECK_INTERVAL = 1000;
    public static Map<String, String> DEFAULT_MYSQL_CONNECTION_PROPERTIES = Maps.newHashMap();

    static {

        // 开启多语句能力
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowMultiQueries", "true");
        // 全量目标数据源加上这个批量的参数
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("rewriteBatchedStatements", "true");
        // 关闭每次读取read-only状态,提升batch性能
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("readOnlyPropagatesToServer", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("connectTimeout", "1000");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("autoReconnect", "true");
        // 将0000-00-00的时间类型返回null
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("zeroDateTimeBehavior", "convertToNull");
        // 直接返回字符串，不做year转换date处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("yearIsDateType", "false");
        // 返回时间类型的字符串,不做时区处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("noDatetimeStringSync", "true");
        // 不处理tinyint转为bit
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("tinyInt1isBit", "false");
        // 16MB，兼容一下ADS不支持mysql，5.1.38+的server变量查询为大写的问题，人肉指定一下最大包大小
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("maxAllowedPacket", "1073741824");
    }

    protected ReentrantReadWriteLock readWriteLock;
    protected AtomicLong seed;
    protected String urlTemplate = "jdbc:mysql://%s";
    protected List<String> nestedAddresses;
    protected LoadingCache<String, DruidDataSource> nestedDataSources;
    protected volatile DruidDataSource proxyDataSource;
    protected ScheduledExecutorService scheduledExecutorService;

//    private volatile static DruidDataSourceWrapper dataSource;
//    public static DruidDataSourceWrapper getInstance() {
//        if (dataSource == null) {
//            synchronized (DruidDataSourceWrapper.class) {
//                if (dataSource == null) {
//                    dataSource = new DruidDataSourceWrapper();
//                }
//            }
//        }
//        return dataSource;
//    }
//
//    public DruidDataSourceWrapper() {
//
//    }

    public DruidDataSourceWrapper(String dbName, String user,
                                  String passwd, String encoding, int minPoolSize,
                                  int maxPoolSize, Map<String, String> params,
                                  List<String> newConnectionSQLs) throws Exception {
        Properties prop = new Properties();
        encoding = StringUtils.isNotBlank(encoding) ? encoding : "utf8mb4";
        if (StringUtils.equalsIgnoreCase(encoding, "utf8mb4")) {
            prop.put("characterEncoding", "utf8");
            if (newConnectionSQLs == null) {
                newConnectionSQLs = new ArrayList<>();
            }
            newConnectionSQLs.add("set names utf8mb4");
        } else {
            prop.put("characterEncoding", encoding);
        }
        prop.putAll(DEFAULT_MYSQL_CONNECTION_PROPERTIES);
        if (params != null) {
            prop.putAll(params);
        }
        setUsername(user);
        setPassword(passwd);
        setTestWhileIdle(true);
        setTestOnBorrow(false);
        setTestOnReturn(false);
        setNotFullTimeoutRetryCount(2);
        setValidConnectionCheckerClassName(MySqlValidConnectionChecker.class.getName());
        setExceptionSorterClassName(MySqlExceptionSorter.class.getName());
        setValidationQuery("SELECT 1");
        setInitialSize(minPoolSize);
        setMinIdle(minPoolSize);
        setMaxActive(maxPoolSize);
        setMaxWait(10 * 1000);
        setTimeBetweenEvictionRunsMillis(60 * 1000);
        setMinEvictableIdleTimeMillis(50 * 1000);
        setUseUnfairLock(true);
        if (newConnectionSQLs != null && newConnectionSQLs.size() > 0) {
            setConnectionInitSqls(newConnectionSQLs);
        }
        setConnectProperties(prop);

        this.readWriteLock = new ReentrantReadWriteLock();
        this.seed = new AtomicLong();
        this.nestedAddresses = new ArrayList<>();
        this.nestedDataSources = CacheBuilder.newBuilder()
            .removalListener(
                (RemovalListener<String, DruidDataSource>) notification -> {
                    DruidDataSource ds = notification.getValue();
                    try {
                        ds.close();
                        logger.info("successfully close datasource for " + notification.getKey());
                    } catch (Exception e) {
                        logger.error("close datasource failed for " + notification.getKey());
                    }
                })
            .build(new CacheLoader<String, DruidDataSource>() {
                @Override
                @ParametersAreNonnullByDefault
                public DruidDataSource load(String address) throws Exception {
                    DruidDataSource ds = cloneDruidDataSource();
                    String url = String.format(urlTemplate, address);
                    if (StringUtils.isNotBlank(dbName)) {
                        url = url + "/" + dbName;
                    }
                    // remove warning msg
                    url = url + "?useSSL=false";
                    ds.setUrl(url);
                    try {
                        ds.init();
                    } catch (Exception e) {
                        throw new Exception("create druid datasource occur exception, with url : "
                            + url + ", user : " + ds.getUsername() + ", passwd : " + ds.getPassword(), e);
                    }
                    return ds;
                }
            });

    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    @SuppressWarnings("unused") // Has to match signature in DataSource
    @Override
    public boolean isWrapperFor(Class<?> iface) {
        // we are not a wrapper of anything
        return false;
    }

    @SuppressWarnings("unused") // Has to match signature in DataSource
    @Override
    public <T> T unwrap(Class<T> iface) {
        //we can't unwrap anything
        return null;
    }

    @Override
    public void init() {
        scan();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r, "server-node-scanner");
            thread.setDaemon(true);
            return thread;
        });
        scheduledExecutorService
            .scheduleAtFixedRate(this::scan, SERVER_CHECK_INTERVAL, SERVER_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void scan() {
        try {
            Set<String> latestServers = getLatestServerAddress();
            Set<String> holdingServers = Sets.newHashSet(nestedAddresses);

            Set<String> toBeAddedServers =
                latestServers.stream().filter(s -> !holdingServers.contains(s)).collect(Collectors.toSet());
            Set<String> toBeRemovedServers =
                holdingServers.stream().filter(s -> !latestServers.contains(s)).collect(Collectors.toSet());

            Set<String> invalidHoldingServers = holdingServers.stream().filter(s -> {
                try (Connection conn = nestedDataSources.getUnchecked(s).getConnection()) {
                    if (conn.isValid(1)) {
                        return false;
                    } else {
                        logger.warn("detected abnormal server node with address {}.", s);
                    }
                } catch (Throwable t) {
                    logger.warn("detected abnormal server node with address {}.", s, t);
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
        } catch (Exception e) {
            logger.error("something goes wrong in server node scan!", e);
        }
    }

    private Set<String> getLatestServerAddress() {
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        return serverInfoMapper.select(c ->
            c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        ).stream().map(s -> String.format("%s:%s", s.getIp(), s.getPort())).collect(Collectors.toSet());
    }

    private Set<String> getLatestNodeAddress() {
        PolarxCNodeInfoMapper cNodeInfoMapper = SpringContextHolder.getObject(PolarxCNodeInfoMapper.class);
        return cNodeInfoMapper.select(c ->
            c.where(status, isEqualTo(1))//0:master, 1:read without htap, 2:read with htap
        ).stream().map(s -> String.format("%s:%s", s.getIp(), s.getPort())).collect(Collectors.toSet());
    }

    private void onServerNodeAdd(Set<String> toBeAddedServers) {
        Set<String> validServers = toBeAddedServers.stream().filter(s -> {
            try (Connection conn = nestedDataSources.getUnchecked(s).getConnection()) {
                if (conn.isValid(1)) {
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
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnectionInternal(username, password);
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     *
     * @return the connection
     * @throws SQLException Connection error
     */
    @Override
    public DruidPooledConnection getConnection() throws SQLException {
        return (DruidPooledConnection) getConnectionInternal(null, null);
    }

    private Connection getConnectionInternal(String username, String password) throws SQLException {
        if (proxyDataSource != null) {
            return username == null ? proxyDataSource.getConnection() :
                proxyDataSource.getConnection(username, password);
        } else {
            try {
                readWriteLock.readLock().lock();

                if (nestedAddresses.size() == 0) {
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
    @Override
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
    @Override
    public javax.sql.PooledConnection getPooledConnection(String username,
                                                          String password) throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    @Override
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


    /*-----------------------------------------------------------------------*/
//      PROPERTIES WHEN NOT USED WITH FACTORY
    /*------------------------------------------------------------------------*/

}
