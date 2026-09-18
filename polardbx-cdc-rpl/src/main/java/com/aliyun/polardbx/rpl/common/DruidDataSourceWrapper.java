/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;
import com.alibaba.druid.pool.vendor.MySqlValidConnectionChecker;
import com.aliyun.polardbx.binlog.CnDataSource;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jdbc.PolarDbxCompatDriver;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.DATASOURCE_CHECK_VALID_TIMEOUT_SEC;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by jiyue
 **/
public class DruidDataSourceWrapper extends DruidDataSource
    implements javax.sql.DataSource, javax.sql.ConnectionPoolDataSource {
    private static final Logger logger = LoggerFactory.getLogger(CnDataSource.class);
    private static final int SERVER_CHECK_INTERVAL = 1000;
    public static Map<String, String> DEFAULT_MYSQL_CONNECTION_PROPERTIES = Maps.newHashMap();

    static {
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.putAll(DataSourceUtil.DEFAULT_MYSQL_CONNECTION_PROPERTIES);
    }

    protected String urlTemplate = "jdbc:mysql://%s";
    protected volatile DruidDataSource proxyDataSource;
    protected ScheduledExecutorService scheduledExecutorService;
    protected int maxWaitTimeMills;

    private final String dbName;
    protected final AtomicReference<ActivePool> activePool = new AtomicReference<>();
    protected final ConcurrentLinkedQueue<ActivePool> retiredPools = new ConcurrentLinkedQueue<>();
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong poolGeneration = new AtomicLong();
    private final AtomicInteger consecutiveHealthFailures = new AtomicInteger();
    private final AtomicInteger consecutiveEmptyTopologySnapshots = new AtomicInteger();
    private volatile long nextSwitchRetryAtMillis;

    static final class ActivePool {
        final String address;
        final DruidDataSource businessDataSource;
        final DruidDataSource healthDataSource;
        final long generation;
        final AtomicBoolean acceptingBorrows = new AtomicBoolean(true);
        final AtomicBoolean retired = new AtomicBoolean(false);
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicBoolean drainTimeoutAlarmed = new AtomicBoolean(false);
        final AtomicInteger pendingBorrows = new AtomicInteger();
        volatile long retiredAtMillis;

        ActivePool(String address, DruidDataSource businessDataSource,
                   DruidDataSource healthDataSource, long generation) {
            this.address = address;
            this.businessDataSource = businessDataSource;
            this.healthDataSource = healthDataSource;
            this.generation = generation;
        }
    }

    static final class ServerSnapshot {
        final Set<String> readyAddresses;
        final Set<String> availableAddresses;
        final Set<String> blacklistedIps;

        ServerSnapshot(Set<String> readyAddresses, Set<String> availableAddresses,
                       Set<String> blacklistedIps) {
            this.readyAddresses = readyAddresses;
            this.availableAddresses = availableAddresses;
            this.blacklistedIps = blacklistedIps;
        }

        boolean isBlacklisted(String address) {
            int delimiter = address.lastIndexOf(':');
            String ip = delimiter < 0 ? address : address.substring(0, delimiter);
            return blacklistedIps.contains(ip.toLowerCase());
        }
    }

    public DruidDataSourceWrapper(String dbName, String user,
                                  String passwd, String encoding, int minPoolSize,
                                  int maxPoolSize, Map<String, String> params,
                                  List<String> newConnectionSQLs) throws Exception {
        this.dbName = dbName;
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
        setDriverClassName(PolarDbxCompatDriver.class.getName());
        setTestWhileIdle(true);
        setTestOnBorrow(false);
        setTestOnReturn(false);
        setNotFullTimeoutRetryCount(2);
        setValidConnectionCheckerClassName(MySqlValidConnectionChecker.class.getName());
        setExceptionSorterClassName(MySqlExceptionSorter.class.getName());
        setValidationQuery("SELECT 1");
        setValidationQueryTimeout(2000);
        setInitialSize(minPoolSize);
        setMinIdle(minPoolSize);
        setMaxActive(maxPoolSize);
        setMaxWait(10 * 1000);
        setTimeBetweenEvictionRunsMillis(60 * 1000);
        setMinEvictableIdleTimeMillis(50 * 1000);
        setUseUnfairLock(true);
        if (newConnectionSQLs != null && !newConnectionSQLs.isEmpty()) {
            setConnectionInitSqls(newConnectionSQLs);
        }
        setConnectProperties(prop);

        this.maxWaitTimeMills = (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(
            ConfigPropMap.getPropertyValue(ConfigKeys.DATASOURCE_CN_GET_TIMEOUT_IN_SECOND)));

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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("## stop pooled druid data source.");
                this.close();
            } catch (Throwable e) {
                logger.warn("##something goes wrong when closing pooled druid data source.", e);
            }
        }));
    }

    void scan() {
        if (closed.get()) {
            return;
        }

        try {
            drainRetiredPools();
            ServerSnapshot snapshot = getLatestServerSnapshot();
            ActivePool current = activePool.get();

            if (current == null) {
                consecutiveHealthFailures.set(0);
                consecutiveEmptyTopologySnapshots.set(0);
                attemptSwitch(null, snapshot.availableAddresses, "initial activation");
                return;
            }

            if (!snapshot.availableAddresses.contains(current.address)) {
                consecutiveHealthFailures.set(0);

                // 空拓扑无法完成原子替换，保留当前池；连续出现时告警，但不主动制造业务不可用。
                if (snapshot.readyAddresses.isEmpty() && !snapshot.isBlacklisted(current.address)) {
                    int emptyCount = consecutiveEmptyTopologySnapshots.incrementAndGet();
                    logger.warn("CN topology is empty for task {}, count={}, keep active node {}",
                        getTaskId(), emptyCount, current.address);
                    if (emptyCount == healthFailureThreshold()) {
                        triggerPoolAlarm(String.format(
                            "CN topology remained empty for %s scans, keep active node %s",
                            emptyCount, current.address));
                    }
                    return;
                } else {
                    consecutiveEmptyTopologySnapshots.set(0);
                }

                // replacement 完成创建和健康校验后再 CAS 发布，失败时当前池继续服务，避免切换空窗。
                attemptSwitch(current, snapshot.availableAddresses,
                    snapshot.isBlacklisted(current.address) ? "active node blacklisted" : "active node removed");
                return;
            }

            consecutiveEmptyTopologySnapshots.set(0);
            current.acceptingBorrows.set(true);
            if (isHealthy(current)) {
                int recoveredFailures = consecutiveHealthFailures.getAndSet(0);
                if (recoveredFailures > 0) {
                    logger.info("CN health recovered, taskId={}, address={}, previousFailures={}",
                        getTaskId(), current.address, recoveredFailures);
                }
                return;
            }

            int failures = consecutiveHealthFailures.incrementAndGet();
            logger.warn("CN independent health check failed, taskId={}, address={}, failures={}/{}",
                getTaskId(), current.address, failures, healthFailureThreshold());
            if (failures >= healthFailureThreshold()) {
                attemptSwitch(current, snapshot.availableAddresses,
                    "health check failed " + failures + " consecutive times");
            }
        } catch (Throwable e) {
            logger.error("something goes wrong in server node scan!", e);
            triggerPoolAlarm("CN pool scanner failed: " + e.getMessage());
        }
    }

    protected ServerSnapshot getLatestServerSnapshot() {
        String config = DynamicApplicationConfig.getString(ConfigKeys.RPL_POOL_CN_BLACK_IP_LIST);
        Set<String> blackIpList = new HashSet<>();
        if (StringUtils.isNotBlank(config)) {
            for (String token : config.trim().toLowerCase().split(RplConstants.COMMA)) {
                if (StringUtils.isNotBlank(token)) {
                    blackIpList.add(token.trim());
                }
            }
        }

        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        Set<String> readyAddresses = serverInfoMapper.select(c ->
            c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        ).stream().map(s -> String.format("%s:%s", s.getIp(), s.getPort())).collect(Collectors.toSet());
        Set<String> availableAddresses = readyAddresses.stream()
            .filter(address -> {
                int delimiter = address.lastIndexOf(':');
                String ip = delimiter < 0 ? address : address.substring(0, delimiter);
                return !blackIpList.contains(ip.toLowerCase());
            }).collect(Collectors.toSet());
        return new ServerSnapshot(readyAddresses, availableAddresses, blackIpList);
    }

    protected ActivePool createActivePool(String address) throws Exception {
        DruidDataSource business = null;
        DruidDataSource health = null;
        try {
            business = cloneDruidDataSource();
            configureDataSourceUrl(business, address);
            business.init();

            health = cloneDruidDataSource();
            configureDataSourceUrl(health, address);
            health.setInitialSize(0);
            health.setMinIdle(0);
            health.setMaxActive(1);
            health.setMaxWait(TimeUnit.SECONDS.toMillis(validationTimeoutSeconds()));
            health.setTestWhileIdle(false);
            health.init();
            return new ActivePool(address, business, health, poolGeneration.incrementAndGet());
        } catch (Throwable t) {
            closeDataSource(health, address, "candidate health");
            closeDataSource(business, address, "candidate business");
            throw new Exception("failed to create CN datasource for " + address, t);
        }
    }

    private void configureDataSourceUrl(DruidDataSource dataSource, String address) {
        String url = String.format(urlTemplate, address);
        if (StringUtils.isNotBlank(dbName)) {
            url = url + "/" + dbName;
        }
        dataSource.setUrl(url + "?allowPublicKeyRetrieval=true&useSSL=false");
    }

    protected boolean isHealthy(ActivePool pool) {
        try (Connection conn = pool.healthDataSource.getConnection()) {
            return conn.isValid(validationTimeoutSeconds());
        } catch (Throwable t) {
            logger.warn("detected abnormal CN node with independent health pool, address={}", pool.address, t);
            return false;
        }
    }

    private boolean attemptSwitch(ActivePool expectedCurrent, Set<String> availableServers, String reason) {
        long now = System.currentTimeMillis();
        if (now < nextSwitchRetryAtMillis || closed.get()) {
            return false;
        }

        List<String> candidates = buildCandidateOrder(availableServers, expectedCurrent);
        for (String candidateAddress : candidates) {
            ActivePool candidate = null;
            try {
                candidate = createActivePool(candidateAddress);
                if (!isHealthy(candidate)) {
                    closeActivePool(candidate, true);
                    continue;
                }

                if (closed.get() || !activePool.compareAndSet(expectedCurrent, candidate)) {
                    closeActivePool(candidate, true);
                    return false;
                }

                candidate.acceptingBorrows.set(true);
                consecutiveHealthFailures.set(0);
                consecutiveEmptyTopologySnapshots.set(0);
                nextSwitchRetryAtMillis = 0;
                if (expectedCurrent != null) {
                    retirePool(expectedCurrent);
                }
                logger.info("CN pool switched atomically, taskId={}, oldAddress={}, newAddress={}, generation={}, "
                        + "reason={}",
                    getTaskId(), expectedCurrent == null ? null : expectedCurrent.address,
                    candidate.address, candidate.generation, reason);
                return true;
            } catch (Throwable t) {
                closeActivePool(candidate, true);
                logger.warn("CN candidate is not ready, taskId={}, address={}, reason={}",
                    getTaskId(), candidateAddress, reason, t);
            }
        }

        nextSwitchRetryAtMillis = now + switchRetryIntervalMillis();
        String oldAddress = expectedCurrent == null ? "none" : expectedCurrent.address;
        String message = String.format(
            "No healthy CN replacement, old=%s, candidates=%s, reason=%s, healthFailures=%s",
            oldAddress, candidates, reason, consecutiveHealthFailures.get());
        logger.warn(message);
        triggerPoolAlarm(message);
        return false;
    }

    private List<String> buildCandidateOrder(Set<String> availableServers, ActivePool current) {
        if (availableServers.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> sorted = new ArrayList<>(availableServers);
        Collections.sort(sorted);
        int startIdx = (int) Math.floorMod(getTaskId(), (long) sorted.size());
        List<String> ordered = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ordered.add(sorted.get((startIdx + i) % sorted.size()));
        }

        // 健康失败时先尝试其他 CN，其他候选都失败后才同地址重建连接池。
        if (current != null && ordered.remove(current.address)) {
            ordered.add(current.address);
        }
        return ordered;
    }

    private void retirePool(ActivePool pool) {
        pool.acceptingBorrows.set(false);
        pool.retired.set(true);
        pool.retiredAtMillis = System.currentTimeMillis();
        retiredPools.add(pool);
    }

    private void drainRetiredPools() {
        for (ActivePool pool : retiredPools) {
            if (pool.pendingBorrows.get() == 0 && pool.businessDataSource.getActiveCount() == 0) {
                if (retiredPools.remove(pool)) {
                    closeActivePool(pool, false);
                    logger.info("retired CN pool drained and closed, taskId={}, address={}, generation={}",
                        getTaskId(), pool.address, pool.generation);
                }
                continue;
            }

            long retiredFor = System.currentTimeMillis() - pool.retiredAtMillis;
            long drainTimeout = drainTimeoutMillis();
            long forceCloseTimeout = Math.max(forceCloseTimeoutMillis(), drainTimeout);
            if (retiredFor >= forceCloseTimeout) {
                if (retiredPools.remove(pool)) {
                    String message = String.format(
                        "Retired CN pool force-close timeout, address=%s, generation=%s, pendingBorrows=%s, active=%s",
                        pool.address, pool.generation, pool.pendingBorrows.get(),
                        pool.businessDataSource.getActiveCount());
                    logger.error(message);
                    closeActivePool(pool, true);
                    triggerPoolAlarm(message);
                }
                continue;
            }

            if (retiredFor >= drainTimeout && pool.drainTimeoutAlarmed.compareAndSet(false, true)) {
                String message = String.format(
                    "Retired CN pool drain timeout, address=%s, generation=%s, pendingBorrows=%s, active=%s",
                    pool.address, pool.generation, pool.pendingBorrows.get(),
                    pool.businessDataSource.getActiveCount());
                logger.warn(message);
                triggerPoolAlarm(message);
            }
        }
    }

    protected int validationTimeoutSeconds() {
        return positiveConfig(ConfigKeys.DATASOURCE_CHECK_VALID_TIMEOUT_SEC, 1);
    }

    protected int healthFailureThreshold() {
        return positiveConfig(ConfigKeys.RPL_POOL_CN_HEALTH_FAILURE_THRESHOLD, 3);
    }

    protected long switchRetryIntervalMillis() {
        return positiveLongConfig(ConfigKeys.RPL_POOL_CN_SWITCH_RETRY_INTERVAL_MILLIS, 5000L);
    }

    protected long drainTimeoutMillis() {
        return positiveLongConfig(ConfigKeys.RPL_POOL_CN_DRAIN_TIMEOUT_MILLIS, 300000L);
    }

    protected long forceCloseTimeoutMillis() {
        return positiveLongConfig(ConfigKeys.RPL_POOL_CN_FORCE_CLOSE_TIMEOUT_MILLIS, 900000L);
    }

    private int positiveConfig(String key, int defaultValue) {
        try {
            Integer value = DynamicApplicationConfig.getInt(key);
            return value != null && value > 0 ? value : defaultValue;
        } catch (Throwable t) {
            logger.warn("invalid config {}, use default {}", key, defaultValue, t);
            return defaultValue;
        }
    }

    private long positiveLongConfig(String key, long defaultValue) {
        try {
            Long value = DynamicApplicationConfig.getLong(key);
            return value != null && value > 0 ? value : defaultValue;
        } catch (Throwable t) {
            logger.warn("invalid config {}, use default {}", key, defaultValue, t);
            return defaultValue;
        }
    }

    protected long getTaskId() {
        try {
            if (TaskContext.getInstance().getTask() != null) {
                return TaskContext.getInstance().getTaskId();
            }
            return Long.parseLong(System.getProperty("taskId", "0"));
        } catch (Throwable t) {
            logger.warn("failed to resolve task id for CN pool, use 0", t);
            return 0L;
        }
    }

    protected void triggerPoolAlarm(String message) {
        try {
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR, getTaskId(), message);
        } catch (Throwable t) {
            // 定时扫描线程不能因告警链路异常而停止后续调度。
            logger.error("failed to trigger CN pool alarm, taskId={}, message={}", getTaskId(), message, t);
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
        }

        long deadline = System.currentTimeMillis() + maxWaitTimeMills;
        while (true) {
            if (closed.get()) {
                throw new PolardbxException("CN datasource has been closed.");
            }

            ActivePool pool = activePool.get();
            if (!isBorrowable(pool)) {
                waitForActivePool(deadline);
                continue;
            }

            pool.pendingBorrows.incrementAndGet();
            if (pool != activePool.get() || !isBorrowable(pool)) {
                pool.pendingBorrows.decrementAndGet();
                continue;
            }

            Connection connection;
            try {
                // 不持有 wrapper 级锁。业务池饱和只阻塞当前借用线程，不阻塞 scanner 发布新 active。
                connection = username == null ? pool.businessDataSource.getConnection() :
                    pool.businessDataSource.getConnection(username, password);
            } catch (SQLException e) {
                if (pool != activePool.get()) {
                    continue;
                }
                throw e;
            } finally {
                pool.pendingBorrows.decrementAndGet();
            }

            // 借连接期间若已完成切换，不把旧池连接交给一个尚未开始的新事务。
            if (pool != activePool.get() || !isBorrowable(pool)) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warn("failed to close connection borrowed from retired CN pool, address={}",
                        pool.address, e);
                }
                continue;
            }
            return connection;
        }
    }

    public void waitNestedAddressReady() {
        long deadline = System.currentTimeMillis() + maxWaitTimeMills;
        while (!isBorrowable(activePool.get())) {
            waitForActivePool(deadline);
        }
    }

    private boolean isBorrowable(ActivePool pool) {
        return pool != null && pool.acceptingBorrows.get() && !pool.retired.get() && !pool.closed.get();
    }

    private void waitForActivePool(long deadline) {
        if (closed.get()) {
            throw new PolardbxException("CN datasource has been closed.");
        }
        if (System.currentTimeMillis() >= deadline) {
            throw new PolardbxException(
                "wait for server node ready timeout, no server node is ready, please retry later.");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new PolardbxException("wait for server node ready interrupted!");
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
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
        return getConnection();
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
        return getConnection();
    }

    @Override
    public void close() {
        close(false);
    }

    public void close(boolean all) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }
            ActivePool current = activePool.getAndSet(null);
            closeActivePool(current, true);
            ActivePool retired;
            while ((retired = retiredPools.poll()) != null) {
                closeActivePool(retired, true);
            }
            if (proxyDataSource != null) {
                proxyDataSource.close();
            }
        } catch (Throwable x) {
            logger.warn("Error during connection pool closure.", x);
        }
    }

    private void closeActivePool(ActivePool pool, boolean force) {
        if (pool == null || !pool.closed.compareAndSet(false, true)) {
            return;
        }
        pool.acceptingBorrows.set(false);
        pool.retired.set(true);
        closeDataSource(pool.healthDataSource, pool.address, "health");
        closeDataSource(pool.businessDataSource, pool.address, force ? "business force" : "business drained");
    }

    private void closeDataSource(DruidDataSource dataSource, String address, String poolType) {
        if (dataSource == null) {
            return;
        }
        try {
            dataSource.close();
            logger.info("successfully closed CN datasource, address={}, poolType={}", address, poolType);
        } catch (Throwable t) {
            logger.warn("failed to close CN datasource, address={}, poolType={}", address, poolType, t);
        }
    }


    /*-----------------------------------------------------------------------*/
//      PROPERTIES WHEN NOT USED WITH FACTORY
    /*------------------------------------------------------------------------*/

}
