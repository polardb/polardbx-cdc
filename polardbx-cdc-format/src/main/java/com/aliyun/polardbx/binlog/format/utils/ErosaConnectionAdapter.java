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
package com.aliyun.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

import java.io.IOException;

public class ErosaConnectionAdapter implements ErosaConnection {

    @Override
    public void connect() throws IOException {

    }

    @Override
    public void reconnect() throws IOException {

    }

    @Override
    public void disconnect() throws IOException {

    }

    @Override
    public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {

    }

    @Override
    public void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills, SinkFunction func)
        throws Exception {

    }

    @Override
    public void dump(long timestamp, SinkFunction func) throws Exception {

    }

    @Override
    public void dump(GTIDSet gtidSet, SinkFunction func) throws Exception {

    }

    @Override
    public ErosaConnection fork() {
        return null;
    }

    @Override
    public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search)
        throws IOException {
        return null;
    }

    @Override
    public BinlogPosition findEndPosition(Long tso) {
        return null;
    }

    @Override
    public long binlogFileSize(String searchFileName) throws IOException {
        return 0;
    }

    @Override
    public String preFileName(String currentFileName) {
        return null;
    }
}
