/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.io.IOException;

/**
 * Created by ziyang.lb
 **/
public class DefaultBinlogFileInfoFetcher implements IBinlogFileInfoFetcher {
    private final ErosaConnection connection;

    public DefaultBinlogFileInfoFetcher(ErosaConnection connection) {
        if (connection instanceof MysqlConnection) {
            this.connection = connection.fork();
        } else if (connection instanceof OssConnection) {
            this.connection = connection;
        } else {
            throw new PolardbxException("invalid connection type " + connection.getClass());
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
