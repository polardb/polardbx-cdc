/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

import java.io.IOException;

/**
 * 通用的Erosa的链接接口, 用于一般化处理mysql/oracle的解析过程
 */
public interface ErosaConnection {

    void connect() throws IOException;

    void reconnect() throws IOException;

    void disconnect() throws IOException;

    /**
     * 用于快速数据查找,和dump的区别在于，seek会只给出部分的数据
     */
    void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception;

    void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills,
              SinkFunction func) throws Exception;

    void dump(long timestamp, SinkFunction func) throws Exception;

    void dump(GTIDSet gtidSet, SinkFunction func) throws Exception;

    ErosaConnection fork();

    LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search) throws IOException;

    BinlogPosition findEndPosition(Long tso);

    long binlogFileSize(String searchFileName) throws IOException;

    String preFileName(String currentFileName);
}
