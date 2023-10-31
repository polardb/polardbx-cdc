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

import com.aliyun.polardbx.binlog.TableCompatibilityProcessor;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

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
        } catch (Exception e) {
            log.error("flyway error", e);
            throw new PolardbxException(e);
        }
        log.info("cdc meta tables init done!");
    }
}
