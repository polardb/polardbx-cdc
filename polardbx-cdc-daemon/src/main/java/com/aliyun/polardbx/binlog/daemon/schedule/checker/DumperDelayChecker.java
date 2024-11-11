/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule.checker;

import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author yanfenglin
 */
public class DumperDelayChecker extends AbstractHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(DumperDelayChecker.class);
    private static final String QUERY_BINLOG_POS = "SHOW MASTER STATUS";
    private long timestamp = System.currentTimeMillis();
    private String binlogFileName;
    private Long pos;

    public DumperDelayChecker(StorageInfo storageInfo) {
        super(storageInfo);
    }

    @Override
    public long doCheck() throws Exception {
        resetMaxPos();
        connection.dump(binlogFileName, pos, -1L, (event, logPosition) -> {
            if (event == null) {
                return true;
            }
            if ((event instanceof RotateLogEvent) ||
                (event instanceof FormatDescriptionLogEvent)) {
                return true;
            }
            timestamp = TimeUnit.SECONDS.toMillis(event.getHeader().getWhen());
            delay = TimeUnit.MILLISECONDS.toSeconds(Math.abs(System.currentTimeMillis() - timestamp));
            logger.info("check storage : " + storageInfo.getStorageMasterInstId() + ", delay : " + delay);
            return false;
        });
        return delay;
    }

    private void resetMaxPos() {
        connection.query(QUERY_BINLOG_POS, rs -> {
            while (rs.next()) {
                binlogFileName = rs.getString(1);
                pos = rs.getLong(2);
                break;
            }
            return null;
        });
    }

}
