/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule.checker;

import com.aliyun.polardbx.binlog.domain.po.StorageInfo;

/**
 * @author yanfenglin
 */
public class ShowSlaveChecker extends AbstractHealthChecker {
    private static final String QUERY_SLAVE_STATUS = "SHOW SLAVE STATUS";

    public ShowSlaveChecker(StorageInfo storageInfo) {
        super(storageInfo);
    }

    @Override
    public long doCheck() {
        delay = connection.query(QUERY_SLAVE_STATUS, rs -> {
            while (rs.next()) {
                return rs.getLong("Seconds_Behind_Master");
            }
            return 0L;
        });
        return delay;
    }

}
