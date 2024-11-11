/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_CLEAN_OLD_VERSION_BINLOG_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_DIR_PATH_PREFIX;
import static com.aliyun.polardbx.binlog.CommonConstants.VERSION_PATH_PREFIX;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
@AllArgsConstructor
@Slf4j
public class OldVersionBinlogCleaner {
    private final long runtimeVersion;

    public void purge() {
        boolean cleanOldVersion = getBoolean(BINLOGX_CLEAN_OLD_VERSION_BINLOG_ENABLED);
        if (!cleanOldVersion) {
            log.info("purge old version binlog files is disabled, will skip ");
            return;
        }

        String basePath = getString(BINLOGX_DIR_PATH_PREFIX);
        File baseDir = new File(basePath);
        File[] files = baseDir.listFiles((dir, name) ->
            StringUtils.startsWith(name, VERSION_PATH_PREFIX) && !StringUtils
                .equals(name, VERSION_PATH_PREFIX + runtimeVersion));

        if (files != null) {
            Arrays.stream(files).forEach(f -> {
                try {
                    FileUtils.forceDelete(f);
                    log.info("old version binlog directory {} is cleaned.", f.getAbsolutePath());
                } catch (IOException e) {
                    throw new PolardbxException("old version binlog delete failed.", e);
                }
            });
        }
    }
}
