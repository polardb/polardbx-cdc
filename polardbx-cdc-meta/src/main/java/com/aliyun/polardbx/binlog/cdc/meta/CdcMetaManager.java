/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ServerVariables;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TableCompatibilityProcessor;
import com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogFileStorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoDynamicSqlSupport.priority;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by Shuguang
 */
@Slf4j
public class CdcMetaManager {

    public CdcMetaManager() {

    }

    public void init() {
        log.info("init cdc meta tables...");
        try {
            Retryer<Object> retryer = RetryerBuilder.newBuilder().retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(50)).build();
            retryer.call(() -> {
                DataSource metaDs = getObject("metaDataSource");
                Flyway flyway = Flyway.configure().table("binlog_schema_history").dataSource(metaDs).load();
                flyway.baseline();
                flyway.repair();
                flyway.migrate();
                // 处理不同版本schema兼容性问题
                TableCompatibilityProcessor.process();
                return null;
            });
            // 写入server_variable功能加一个控制开关，默认是false，不写入db
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.META_WRITE_ALL_VARIABLE_TO_DB_SWITCH)) {
                writeServerVariableToMetaDB();
            }

        } catch (Exception e) {
            log.error("flyway error", e);
            throw new PolardbxException(e);
        }
        initBinlogFileStorageInfo();
        log.info("cdc meta tables init done!");
    }

    private void writeServerVariableToMetaDB() {
        List<Pair<String, String>> configList = new ArrayList<>();
        for (String config : ServerVariables.variables) {
            String value = DynamicApplicationConfig.getString(config);
            if (StringUtils.isNotBlank(value)) {
                configList.add(Pair.of(config, value));
            }
        }

        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");
        String sql = "INSERT IGNORE INTO binlog_system_config(config_key, config_value) VALUES (?, ?)";
        metaJdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, configList.get(i).getLeft());
                ps.setString(2, configList.get(i).getRight());
            }

            @Override
            public int getBatchSize() {
                return configList.size();
            }
        });
    }

    /**
     * 插入binlog_file_storage_info表，测试用
     */
    private void initBinlogFileStorageInfo() {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)) {
            BinlogFileStorageInfoMapper binlogFileStorageInfoMapper =
                SpringContextHolder.getObject(BinlogFileStorageInfoMapper.class);
            Optional<BinlogFileStorageInfo> binlogFileStorageInfoOptional = binlogFileStorageInfoMapper.selectOne(
                s -> s.where(BinlogFileStorageInfoDynamicSqlSupport.instId,
                        isEqualTo(getString(ConfigKeys.POLARX_INST_ID)))
                    .orderBy(priority.descending())
                    .limit(1));
            if (!binlogFileStorageInfoOptional.isPresent()) {
                String endpoint = DynamicApplicationConfig.getString(ConfigKeys.OSS_ENDPOINT);
                String bucketName = DynamicApplicationConfig.getString(ConfigKeys.COMMON_BUCKET_NAME);
                BinlogFileStorageInfo binlogFileStorageInfo = new BinlogFileStorageInfo();
                binlogFileStorageInfo.setInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
                binlogFileStorageInfo.setEngine("S3");
                binlogFileStorageInfo.setRegionId("aws-global");
                binlogFileStorageInfo.setExternalEndpoint(endpoint);
                binlogFileStorageInfo.setInternalVpcEndpoint(endpoint);
                binlogFileStorageInfo.setInternalClassicEndpoint(endpoint);
                binlogFileStorageInfo.setFileUri("s3://" + bucketName + "/");
                binlogFileStorageInfo.setAccessKeyId(DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID));
                binlogFileStorageInfo.setAccessKeySecret(
                    DynamicApplicationConfig.getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET));
                binlogFileStorageInfo.setCachePolicy(1L);
                binlogFileStorageInfo.setDeletePolicy(1L);
                binlogFileStorageInfo.setStatus(1L);
                binlogFileStorageInfo.setEndpointOrdinal(0L);
                binlogFileStorageInfo.setPriority(10L);
                binlogFileStorageInfoMapper.insert(binlogFileStorageInfo);
            }
        }
    }
}
