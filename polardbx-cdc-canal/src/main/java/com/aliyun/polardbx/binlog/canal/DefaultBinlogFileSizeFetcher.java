package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.io.IOException;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL;

/**
 * Created by ziyang.lb
 **/
public class DefaultBinlogFileSizeFetcher implements IBinlogFileSizeFetcher {
    private final ErosaConnection connection;

    public DefaultBinlogFileSizeFetcher(ErosaConnection connection) {
        if (connection instanceof MysqlConnection) {
            this.connection = connection.fork();
        } /*else if (connection instanceof OssConnection) {
            this.connection = connection;
        }*/ else {
            throw new PolardbxException("invalid connection type " + connection.getClass());
        }
    }

    @Override
    public long fetch(String fileName) throws IOException {
        sleep();
        if (connection instanceof MysqlConnection) {
            try {
                connection.connect();
                return connection.binlogFileSize(fileName);
            } finally {
                connection.disconnect();
            }
        } else {
            return connection.binlogFileSize(fileName);
        }
    }

    private void sleep() {
        try {
            int tsoHeartBeatInterval = DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL);
            Thread.sleep(Math.min(tsoHeartBeatInterval * 5, 2000));
        } catch (InterruptedException e) {
        }
    }
}
