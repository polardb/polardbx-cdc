/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;

import java.io.IOException;

/**
 * Created by ziyang.lb
 **/
public class DefaultBinlogFileInfoFetcher implements IBinlogFileInfoFetcher {
    private final ErosaConnection connection;

    public DefaultBinlogFileInfoFetcher(ErosaConnection connection) {
        if (connection instanceof MysqlConnection) {
            this.connection = connection.fork();
        } else {
            this.connection = connection;
        }

    }

    @Override
    public long fetch(String fileName) throws IOException {
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

    @Override
    public boolean isFileExisting(String fileName) throws IOException {
        if (connection instanceof MysqlConnection) {
            try {
                connection.connect();
                return connection.binlogFileSize(fileName) != -1L;
            } finally {
                connection.disconnect();
            }
        } else {
            return connection.binlogFileSize(fileName) != -1L;
        }
    }
}
