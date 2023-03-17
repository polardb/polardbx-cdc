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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
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
            Optional<BinlogOssRecord> record =
                binlogOssRecordService.getRecordByLastTso(logFileManager.getGroupName(), logFileManager.getStreamName(),
                    recoverTso);
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
