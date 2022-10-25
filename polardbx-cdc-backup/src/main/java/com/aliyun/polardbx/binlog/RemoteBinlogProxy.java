/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.action.Appender;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.aliyun.polardbx.binlog.lindorm.LindormConfig;
import com.aliyun.polardbx.binlog.oss.OssConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configKey;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class RemoteBinlogProxy {

    public static final int PART_SIZE = DynamicApplicationConfig.getInt(BINLOG_BACKUP_UPLOAD_PART_SIZE);
    private static final Logger logger = LoggerFactory.getLogger(RemoteBinlogProxy.class);
    private static final RemoteBinlogProxy instance = new RemoteBinlogProxy();
    private IRemoteManager delegate;
    private boolean backSwitch = false;
    private IConfigurator configurator;

    private RemoteBinlogProxy() {
        config();
    }

    public static RemoteBinlogProxy getInstance() {
        return instance;
    }

    public List<String> listBuckets() {
        checkDeleator();
        return delegate.listBuckets();
    }

    public String prepareDownLink(String fileName, long interval) {
        checkDeleator();
        return delegate.prepareDownloadLink(fileName, interval);
    }

    public void download(String fileName, String localPath) {
        checkDeleator();
        delegate.download(fileName, localPath);
    }

    public String getMd5(String fileName) {
        checkDeleator();
        return delegate.getMd5(fileName);
    }

    public long getSize(String fileName) {
        checkDeleator();
        return delegate.getSize(fileName);
    }

    public void delete(String fileName) {
        checkDeleator();
        delegate.delete(fileName);
    }

    public void deleteAll(String path) {
        checkDeleator();
        delegate.deleteAll(path);
    }

    public boolean isObjectsExistForPrefix(String pathPrefix) {
        checkDeleator();
        return delegate.isObjectsExistForPrefix(pathPrefix);
    }

    public List<String> listFiles(String path) {
        checkDeleator();
        return delegate.listFiles(path);
    }

    public byte[] getFileData(String fileName) {
        checkDeleator();
        return delegate.getObjectData(fileName);
    }

    private void checkDeleator() {
        if (delegate == null) {
            delegate = configurator.prepare();
        }
    }

    public Appender providerMultiAppender(String fileName, long fileLength) {
        checkDeleator();
        return delegate.providerMultiAppender(fileName, fileLength);
    }

    public Appender providerAppender(String fileName) {
        checkDeleator();
        return delegate.providerAppender(fileName);
    }

    public boolean needSwitchMultilUpload(long size) {
        checkDeleator();
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

    private void doConfig() {
        configurator.doConfig();
        backSwitch = true;
    }

    private void config() {
        String backupType = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_BACKUP_TYPE);
        BinlogBackupTypeEnum backupTypeEnum = BinlogBackupTypeEnum.typeOf(backupType);
        if (backupTypeEnum != null) {
            if (backupTypeEnum == BinlogBackupTypeEnum.OSS) {
                OssConfig ossConfig = new OssConfig();
                ossConfig.setAccessKeyId(DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID));
                ossConfig.setAccessKeySecret(DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET));
                ossConfig.setBucketName(DynamicApplicationConfig.getString(ConfigKeys.OSS_BUCKET));
                ossConfig.setEndpoint(DynamicApplicationConfig.getString(ConfigKeys.OSS_ENDPOINT));
                ossConfig.setPolardbxInstance(buildPolarxInstId());
                configOss(ossConfig);
            } else if (backupTypeEnum == BinlogBackupTypeEnum.LINDORM) {
                LindormConfig lindormConfig = new LindormConfig();
                lindormConfig.setAccessKey(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ACCESSKEY_ID));
                lindormConfig
                    .setAccessSecret(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ACCESSKEY_ID_SECRET));
                lindormConfig.setBucket(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_BUCKET));
                lindormConfig.setDownloadPort(DynamicApplicationConfig.getInt(ConfigKeys.LINDORM_DOWNLOAD_PORT));
                lindormConfig.setIp(DynamicApplicationConfig.getString(ConfigKeys.LINDORM_ENDPOINT));
                lindormConfig.setPort(DynamicApplicationConfig.getInt(ConfigKeys.LINDORM_PORT));
                lindormConfig.setPolardbxInstance(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
                configLindorm(lindormConfig);
            }
        }
    }

    //实验室环境专用
    private String buildPolarxInstId() {
        String ossBucket = DynamicApplicationConfig.getString(ConfigKeys.OSS_BUCKET);
        String polarxInstId = DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID);
        if (StringUtils.equals(ossBucket, "polarx-cdc-lab-test-bucket")) {
            long timestamp = waitClusterReady();
            return polarxInstId + "-" + timestamp;
        }
        return polarxInstId;
    }

    private long waitClusterReady() {
        SystemConfigInfoMapper systemConfigInfoMapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        long start = System.currentTimeMillis();
        while (true) {
            List<SystemConfigInfo> list = systemConfigInfoMapper.select(
                s -> s.where(configKey, isEqualTo(CLUSTER_SNAPSHOT_VERSION_KEY)));
            if (!list.isEmpty()) {
                return list.get(0).getGmtCreated().getTime();
            }

            if (System.currentTimeMillis() - start > 120 * 1000) {
                logger.error("wait cluster ready timeout!!");
                Runtime.getRuntime().halt(1);
            }
        }
    }

}
