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
package com.aliyun.polardbx.binlog.clean;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_CLEAN_OLD_VERSION_BINLOG_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_DIR_PATH_PREFIX;
import static com.aliyun.polardbx.binlog.Constants.VERSION_PATH_PREFIX;
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
        boolean cleanOldVersion = getBoolean(BINLOG_X_CLEAN_OLD_VERSION_BINLOG_ENABLE);
        if (!cleanOldVersion) {
            log.info("purge old version binlog files is disabled, will skip ");
            return;
        }

        String basePath = getString(BINLOG_X_DIR_PATH_PREFIX);
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
