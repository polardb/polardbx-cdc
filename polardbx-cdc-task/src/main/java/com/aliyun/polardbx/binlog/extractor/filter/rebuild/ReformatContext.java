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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.format.BinlogBuilder;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;

public class ReformatContext {
    private static final ThreadLocal<AutoExpandBuffer> localBuffer = new ThreadLocal<>();
    private final String defaultCharset;
    private final String charsetServer;
    private final int lowerCaseTableNames;
    private final String storageInstanceId;
    private long serverId;
    private IteratorBuffer it;
    private String virtualTSO;

    private String binlogFile;

    public ReformatContext(String defaultCharset, String charsetServer,
                           int lowerCaseTableNames, String storageInstanceId) {
        this.defaultCharset = defaultCharset;
        this.charsetServer = charsetServer;
        this.lowerCaseTableNames = lowerCaseTableNames;
        this.storageInstanceId = storageInstanceId;
    }

    public static byte[] toByte(BinlogBuilder binlog) throws Exception {
        AutoExpandBuffer buf = getBuffer();
        int size = binlog.write(buf);
        byte[] newBuf = new byte[size];
        System.arraycopy(buf.toBytes(), 0, newBuf, 0, size);
        return newBuf;
    }

    private static AutoExpandBuffer getBuffer() {
        AutoExpandBuffer buf = localBuffer.get();
        if (buf == null) {
            buf = new AutoExpandBuffer(1024 * 1024, 1024);
            localBuffer.set(buf);
        }
        buf.reset();
        return buf;
    }

    public IteratorBuffer getIt() {
        return it;
    }

    public void setIt(IteratorBuffer it) {
        this.it = it;
    }

    public String getVirtualTSO() {
        return virtualTSO;
    }

    public void setVirtualTSO(String virtualTSO) {
        this.virtualTSO = virtualTSO;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String getDefaultCharset() {
        return defaultCharset;
    }

    public String getCharsetServer() {
        return charsetServer;
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public int getLowerCaseTableNames() {
        return lowerCaseTableNames;
    }

    public String getStorageInstanceId() {
        return storageInstanceId;
    }
}
