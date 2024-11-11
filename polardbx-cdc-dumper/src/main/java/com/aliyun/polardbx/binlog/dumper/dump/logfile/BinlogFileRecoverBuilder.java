/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogFileRecoverBuilder {
    private static final BinlogOssRecordService binlogOssRecordService = getObject(BinlogOssRecordService.class);

    public static RecoverInfo build(LogFileManager logFileManager, String recoverTso, String recoverFileName) {
        String fileName;
        String startTso;

        if (StringUtils.isBlank(recoverTso) || recoverTso.equals(ExecutionConfig.ORIGIN_TSO)) {
            log.info("build recover tso from origin tso");
            fileName =
                BinlogFileUtil.getFirstBinlogFileName(logFileManager.getGroupName(), logFileManager.getStreamName());
            startTso = "";
        } else {
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            Optional<BinlogOssRecord> record =
                binlogOssRecordService.getRecordByTso(logFileManager.getGroupName(), logFileManager.getStreamName(),
                    clusterId, recoverTso);
            if (record.isPresent()) {
                if (!StringUtils.equals(record.get().getBinlogFile(), recoverFileName)) {
                    throw new PolardbxException(String
                        .format("file name is mismatched with recover file name, %s:%s.",
                            record.get().getBinlogFile(), recoverFileName));
                }

                // last tso, need rotate to next file
                fileName = BinlogFileUtil.getNextBinlogFileName(record.get().getBinlogFile());
                log.info("build recover tso by last tso:{}, from binlog file:{}", recoverTso, fileName);
            } else {
                if (StringUtils.isBlank(recoverFileName)) {
                    throw new PolardbxException(
                        String.format("file name can`t be empty for recover tso %s.", recoverTso));
                }
                fileName = recoverFileName;
                log.info("build recover tso by middle tso:{}, from binlog file{}", recoverTso, fileName);
            }
            startTso = recoverTso;
        }

        return new RecoverInfo(fileName, startTso);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecoverInfo {
        private String fileName;
        private String startTso;
    }
}
