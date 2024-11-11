/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
