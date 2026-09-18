/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RDS/DBS Binlog列表查询API，支持分页查询、并发限流和自动重试
 */
public class DescribeRdsBinlogListApi {

    private static final Logger logger = LoggerFactory.getLogger(DescribeRdsBinlogListApi.class);

    /**
     * 并发控制信号量
     */
    private static volatile Semaphore semaphore;

    /**
     * 当前信号量的permits数，用于检测配置变更，-1表示未初始化
     */
    private static volatile int currentPermits = -1;

    /**
     * 获取限流信号量，支持动态配置更新
     *
     * @return 当前生效的Semaphore实例
     */
    public static Semaphore getSemaphore() {
        int configuredPermits = DynamicApplicationConfig.getInt(
            ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY);
        if (configuredPermits != currentPermits) {
            synchronized (DescribeRdsBinlogListApi.class) {
                if (configuredPermits != currentPermits) {
                    logger.info("describeBinlogFiles concurrency limit changed from {} to {}",
                        currentPermits, configuredPermits);
                    semaphore = new Semaphore(configuredPermits);
                    currentPermits = configuredPermits;
                }
            }
        }
        return semaphore;
    }

    /**
     * 重置信号量状态，仅用于测试
     */
    public static void resetSemaphore() {
        synchronized (DescribeRdsBinlogListApi.class) {
            semaphore = null;
            currentPermits = -1;
        }
    }

    /**
     * 构建重试器，支持配置化的重试次数和重试间隔
     *
     * @return 重试器实例
     */
    static <T> Retryer<T> buildRetryer() {
        int retryCount = DynamicApplicationConfig.getInt(
            ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT);
        long retryIntervalMs = DynamicApplicationConfig.getLong(
            ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS);
        return RetryerBuilder.<T>newBuilder()
            .retryIfException(e -> isRetryableException(e))
            .withWaitStrategy(WaitStrategies.fixedWait(retryIntervalMs, TimeUnit.MILLISECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(retryCount))
            .build();
    }

    /**
     * 判断异常是否可重试，包括超时、连接异常、IO异常等
     *
     * @param e 异常
     * @return 是否可重试
     */
    public static boolean isRetryableException(Throwable e) {
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        if (e instanceof java.net.ConnectException) {
            return true;
        }
        if (e instanceof java.io.IOException) {
            return true;
        }
        // 检查cause链中是否包含可重试异常
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isRetryableException(cause);
        }
        return false;
    }

    /**
     * 分页查询binlog文件列表，并发访问受限流信号量保护，单次分页请求支持自动重试
     *
     * @param dbInstanceName 数据库实例名
     * @param uid 用户ID
     * @param bid 用户BID
     * @param begin 查询开始时间戳
     * @param end 查询结束时间戳
     * @param maxRecordsPerPage 每页最大记录数
     * @param useDbsApi 是否使用DBS API
     * @return binlog文件列表
     * @throws Exception 查询异常或限流中断异常
     */
    public static List<BinlogFile> describeBinlogFiles(
        String dbInstanceName,
        String uid,
        String bid,
        long begin,
        long end,
        Integer maxRecordsPerPage,
        boolean useDbsApi) throws Exception {
        Semaphore sem = getSemaphore();
        sem.acquire();
        try {
            return doDescribeBinlogFiles(dbInstanceName, uid, bid, begin, end, maxRecordsPerPage, useDbsApi);
        } finally {
            sem.release();
        }
    }

    /**
     * 实际执行分页查询binlog文件列表，每次分页请求带重试保护
     */
    private static List<BinlogFile> doDescribeBinlogFiles(
        String dbInstanceName,
        String uid,
        String bid,
        long begin,
        long end,
        Integer maxRecordsPerPage,
        boolean useDbsApi) throws Exception {
        List<BinlogFile> totalRecords = new ArrayList<>();
        int counts = 0;
        int pageNumber = 1;
        do {
            if (useDbsApi) {
                DescribeUnifyArchiveLogFilesResult result =
                    fetchDbsPageWithRetry(dbInstanceName, uid, bid, begin, end, maxRecordsPerPage, pageNumber++);
                totalRecords.addAll(result.getData().getContent().stream().map(BinlogFile::createFrom)
                    .collect(Collectors.toList()));
                counts += result.getData().getPageSize();
                if (result.getData().getTotalElements() <= counts) {
                    break;
                }
            } else {
                DescribeBinlogFilesResult result =
                    fetchRdsPageWithRetry(dbInstanceName, uid, bid, begin, end, maxRecordsPerPage, pageNumber++);
                totalRecords.addAll(result.getItems());
                counts += result.getItemsNumbers();
                if (result.getTotalRecords() <= counts) {
                    break;
                }
            }
        } while (true);
        return totalRecords;
    }

    /**
     * 带重试的RDS API分页请求
     */
    private static DescribeBinlogFilesResult fetchRdsPageWithRetry(
        String dbInstanceName, String uid, String bid,
        long begin, long end, Integer maxRecordsPerPage, int pageNumber) throws Exception {
        Retryer<DescribeBinlogFilesResult> retryer = buildRetryer();
        try {
            return retryer.call(() -> {
                logger.info("fetchRdsPage, instance={}, page={}", dbInstanceName, pageNumber);
                return RdsApi.describeBinlogFiles(dbInstanceName, uid, bid,
                    RdsApi.formatUTCTZ(new Date(begin)), RdsApi.formatUTCTZ(new Date(end)),
                    maxRecordsPerPage, pageNumber);
            });
        } catch (Exception e) {
            logger.error("fetchRdsPage failed after retries, instance={}, page={}",
                dbInstanceName, pageNumber, e);
            throw e;
        }
    }

    /**
     * 带重试的DBS API分页请求
     */
    private static DescribeUnifyArchiveLogFilesResult fetchDbsPageWithRetry(
        String dbInstanceName, String uid, String bid,
        long begin, long end, Integer maxRecordsPerPage, int pageNumber) throws Exception {
        Retryer<DescribeUnifyArchiveLogFilesResult> retryer = buildRetryer();
        try {
            return retryer.call(() -> {
                logger.info("fetchDbsPage, instance={}, page={}", dbInstanceName, pageNumber);
                return DbsApi.describeUnifyArchiveLogFiles(
                    dbInstanceName, uid, bid, begin, end, maxRecordsPerPage, pageNumber);
            });
        } catch (Exception e) {
            logger.error("fetchDbsPage failed after retries, instance={}, page={}",
                dbInstanceName, pageNumber, e);
            throw e;
        }
    }
}
