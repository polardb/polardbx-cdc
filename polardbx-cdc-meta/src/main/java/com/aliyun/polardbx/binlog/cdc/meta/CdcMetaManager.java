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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TableCompatibilityProcessor;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;

/**
 * Created by Shuguang
 */
@Slf4j
public class CdcMetaManager {
    private final String clusterId;
    private final String clusterType;

    public CdcMetaManager(String clusterId, String clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
    }

    public void init() {
        log.info("init cdc meta tables...");
        // Create the Flyway instance and point it to the database
        Flyway flyway = Flyway.configure().table("binlog_schema_history").dataSource(
            SpringContextHolder.getPropertiesValue("metaDb_url"),
            SpringContextHolder.getPropertiesValue("metaDb_username"),
            tryDecryptPassword(SpringContextHolder.getPropertiesValue("metaDb_password"))).load();

        flyway.baseline();
        flyway.repair();

        // Start the migration
        flyway.migrate();

        // try process compatibility
        TableCompatibilityProcessor.process();

        log.info("cdc meta tables init done!");
    }

    private String tryDecryptPassword(String password) {
        boolean useEncryptedPassword = DynamicApplicationConfig.getBoolean(ConfigKeys.USE_ENCRYPTED_PASSWORD);
        return useEncryptedPassword ? PasswdUtil.decryptBase64(password) : password;
    }
}
