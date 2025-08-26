/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoMapper;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogFileStorageInfo;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.aliyun.polardbx.binlog.enums.BinlogBackupType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.remote.channel.LindormBinlogFileReadChannel;
import com.aliyun.polardbx.binlog.remote.channel.OssBinlogFileReadChannel;
import com.aliyun.polardbx.binlog.remote.channel.S3BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.remote.lindorm.LindormConfig;
import com.aliyun.polardbx.binlog.remote.lindorm.LindormManager;
import com.aliyun.polardbx.binlog.remote.oss.OssConfig;
import com.aliyun.polardbx.binlog.remote.oss.OssManager;
import com.aliyun.polardbx.binlog.remote.s3.S3Manager;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.channels.Channel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_TYPE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoDynamicSqlSupport.priority;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configKey;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author yudong
 * <p>
 * 所有方法的fileName不是单纯的文件名，而是拼接了group name和stream name前缀的文件名
 */
public class RemoteBinlogProxy {

    public static final int PART_SIZE = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE);
    private static final Logger logger = LoggerFactory.getLogger(RemoteBinlogProxy.class);
    private static final RemoteBinlogProxy instance = new RemoteBinlogProxy();
    private IRemoteManager delegate;
    private boolean backSwitch = false;
    private IConfigurator configurator;
    private String instId;

    private RemoteBinlogProxy() {
        config();
    }

    public static RemoteBinlogProxy getInstance() {
        return instance;
    }

    public List<String> listBuckets() {
        checkDelegator();
        return delegate.listBuckets();
    }

    public String prepareDownLink(String fileName, long interval) {
        checkDelegator();
        return delegate.prepareDownloadLink(fileName, interval);
    }

    public void download(String fileName, String localPath, DownloadParameter downloadParameter)
        throws Throwable {
        checkDelegator();
        delegate.download(fileName, localPath, downloadParameter);
    }

    public String getMd5(String fileName) {
        checkDelegator();
        return delegate.getMd5(fileName);
    }

    public long getSize(String fileName) {
        checkDelegator();
        return delegate.getSize(fileName);
    }

    public void deleteFile(String fileName) {
        checkDelegator();
        delegate.delete(fileName);
    }

    public void deleteAll(String path) {
        checkDelegator();
        delegate.deleteAll(path);
    }

    public boolean isObjectsExistForPrefix(String pathPrefix) {
        checkDelegator();
        return delegate.isObjectsExistForPrefix(pathPrefix);
    }

    public List<String> listFiles(String path) {
        checkDelegator();
        return delegate.listFiles(path);
    }

    public byte[] getFileData(String fileName) {
        checkDelegator();
        return delegate.getObjectData(fileName);
    }

    private void checkDelegator() {
        if (delegate == null) {
            delegate = configurator.prepare();
        }
    }

    public Appender providerMultiAppender(String fileName, long fileLength) {
        checkDelegator();
        return delegate.providerMultiAppender(fileName, fileLength);
    }

    public Appender providerAppender(String fileName) {
        checkDelegator();
        return delegate.providerAppender(fileName);
    }

    public boolean supportMultiUpload() {
        checkDelegator();
        return delegate.supportMultiAppend();
    }

    public boolean needSwitchMultiUpload(long size) {
        checkDelegator();
        return delegate.useMultiAppender(size);
    }

    public boolean isBackupOn() {
        if (!backSwitch) {
            logger.warn("binlog remote backup not config!");
        }
        return backSwitch;
    }

    public void configOss(OssConfig ossConfig) {
        configurator = new AsyncConfigurator(ossConfig, null);
        doConfig();
    }

    public void configLindorm(LindormConfig lindormConfig) {
        configurator = new AsyncConfigurator(null, lindormConfig);
        doConfig();
    }

    public void configCommon(CommonConfig commonConfig) {
        configurator = new AsyncConfigurator(null, null, commonConfig);
        doConfig();
    }

    private void doConfig() {
        configurator.doConfig();
        backSwitch = true;
    }

    private void config() {
        String backupType = DynamicApplicationConfig.getString(BINLOG_BACKUP_TYPE);
        BinlogBackupType backupTypeEnum = BinlogBackupType.typeOf(backupType);
        instId = buildPolarxInstId();
        logger.info("try config ...");
        if (backupTypeEnum != BinlogBackupType.NULL) {
            if (backupTypeEnum == BinlogBackupType.OSS) {
                OssConfig ossConfig = new OssConfig();
                ossConfig.setAccessKeyId(DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID));
                ossConfig.setAccessKeySecret(DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET));
                ossConfig.setBucketName(DynamicApplicationConfig.getString(ConfigKeys.OSS_BUCKET));
                ossConfig.setEndpoint(DynamicApplicationConfig.getString(ConfigKeys.OSS_ENDPOINT));
                ossConfig.setPolardbxInstance(instId);
                configOss(ossConfig);
            } else if (backupTypeEnum == BinlogBackupType.LINDORM) {
                LindormConfig lindormConfig = new LindormConfig();
                lindormConfig.setAccessKey(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ACCESSKEY_ID));
                lindormConfig
                    .setAccessSecret(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ACCESSKEY_ID_SECRET));
                // lindorm only support lower case bucket name
                lindormConfig.setBucket(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_BUCKET).toLowerCase());
                lindormConfig.setS3Port(
                    Integer.valueOf(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_S3_PORT)));
                lindormConfig.setIp(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ENDPOINT));
                lindormConfig.setThriftPort(
                    Integer.valueOf(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_THRIFT_PORT)));
                lindormConfig.setPolardbxInstance(instId);
                configLindorm(lindormConfig);
            } else {
                logger.info("try config remote other remote storage ...");
                Retryer<Optional<BinlogFileStorageInfo>> retryer =
                    RetryerBuilder.<Optional<BinlogFileStorageInfo>>newBuilder()
                    .retryIfException()
                    .retryIfResult(s -> !s.isPresent())
                    .withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
                        .withStopStrategy(StopStrategies.stopAfterAttempt(600))
                    .build();
                BinlogFileStorageInfoMapper fileStorageInfoMapper =
                    SpringContextHolder.getObject(BinlogFileStorageInfoMapper.class);
                Optional<BinlogFileStorageInfo> fileStorageInfo = Optional.empty();
                try {
                    fileStorageInfo = retryer.call(() -> fileStorageInfoMapper.selectOne(
                        s -> s.where(BinlogFileStorageInfoDynamicSqlSupport.instId,
                                isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID)))
                            .orderBy(priority.descending())
                            .limit(1)));
                } catch (Exception e) {
                    logger.error("failed to init remote storage from fileStorageInfo.", e);
                }
                BinlogFileStorageInfo storageInfo = fileStorageInfo.get();
                List<String> endpoints =
                    ImmutableList.of(storageInfo.getExternalEndpoint(), storageInfo.getInternalClassicEndpoint(),
                        storageInfo.getInternalVpcEndpoint());
                int ordinal = (int) Math.min(storageInfo.getEndpointOrdinal(), endpoints.size() - 1);
                String endpoint = endpoints.get(ordinal);
                CommonConfig commonConfig = new CommonConfig(
                    storageInfo.getEngine(),
                    endpoint,
                    storageInfo.getRegionId(),
                    storageInfo.getAccessKeyId(),
                    storageInfo.getAccessKeySecret(),
                    DynamicApplicationConfig.getString(ConfigKeys.COMMON_BUCKET_NAME).replaceAll("^['\"]|['\"]$", ""),
                    instId
                );
                logger.info("try config remote storage with " + commonConfig);
                configCommon(commonConfig);
            }
        }
    }

    // 实验室环境专用
    private String buildPolarxInstId() {
        String ossBucket = DynamicApplicationConfig.getString(ConfigKeys.OSS_BUCKET);
        String polarxInstId = DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID);
        if (StringUtils.equals(ossBucket, "polarx-cdc-lab-bucket")) {
            waitClusterReady();
            String randomPolarxInstId = polarxInstId + "-" + getServerUid();
            logger.info("random polarx instance id for lab test is " + randomPolarxInstId);
            logger.info("oss binlog file path is " + BinlogFileUtil.buildRemoteFileFullName("", randomPolarxInstId));
            return randomPolarxInstId;
        }
        return polarxInstId;
    }

    private void waitClusterReady() {
        SystemConfigInfoMapper systemConfigInfoMapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        long start = System.currentTimeMillis();
        while (true) {
            List<SystemConfigInfo> list = systemConfigInfoMapper.select(
                s -> s.where(configKey, isEqualTo(CLUSTER_SNAPSHOT_VERSION_KEY)));
            if (!list.isEmpty()) {
                return;
            }

            if (System.currentTimeMillis() - start > 120 * 1000) {
                logger.error("wait cluster ready timeout!!");
                Runtime.getRuntime().halt(1);
            }
        }
    }

    private String getServerUid() {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        return jdbcTemplate.query("show variables like '%server_uuid%'", rs -> {
            if (rs.next()) {
                return rs.getString("Value");
            } else {
                throw new PolardbxException("query server_uuid error");
            }
        });
    }

    public Channel prepareReadChannel(String fileName) {
        if (!isBackupOn()) {
            return null;
        }
        checkDelegator();
        if (delegate instanceof OssManager) {
            OssManager provider = (OssManager) delegate;
            return new OssBinlogFileReadChannel(
                provider.getOssClient(),
                provider.getBucket(),
                BinlogFileUtil.buildRemoteFileFullName(fileName, instId));
        } else if (delegate instanceof LindormManager) {
            LindormManager provider = (LindormManager) delegate;
            return new LindormBinlogFileReadChannel(
                provider.getS3Client(),
                provider.getBucket(),
                provider.getLindormFileName(fileName));
        } else if (delegate instanceof S3Manager) {
            S3Manager provider = (S3Manager) delegate;
            return new S3BinlogFileReadChannel(
                provider.getS3ClientVirtualPath(),
                provider.getBucket(),
                BinlogFileUtil.buildRemoteFileFullName(fileName, instId));
        }
        return null;
    }

    public boolean isS3() {
        return delegate instanceof S3Manager;
    }
}
