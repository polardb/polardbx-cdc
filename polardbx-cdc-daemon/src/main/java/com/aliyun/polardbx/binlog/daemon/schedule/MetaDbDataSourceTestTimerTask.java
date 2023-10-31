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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.Statement;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * @author yudong
 * @since 2023/7/31 21:02
 **/
@Slf4j
public class MetaDbDataSourceTestTimerTask extends AbstractBinlogTimerTask {

    private final DataSource metaDataSource;

    public MetaDbDataSourceTestTimerTask(String clusterId, String clusterType, String name, long interval) {
        super(clusterId, clusterType, name, interval);
        metaDataSource = getObject("metaDataSource");
    }

    @Override
    public void exec() {
        try (Connection conn = metaDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS t_test(id int primary key)");
            log.info("test success");
            log.info("meta url:{}", conn.getMetaData().getURL());
        } catch (Exception e) {
            log.error("test error");
        }
    }
}
