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
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.download.rds.BinlogFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class ContinuesURLLogFetcher extends LogFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ContinuesURLLogFetcher.class);
    private static final long ALERT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private URLLogFetcher urlLogFetcher;
    private BinlogFile binlogFile;
    private LinkedList<BinlogFile> binlogFileQueue;
    private String storageInstanceId;

    public ContinuesURLLogFetcher(String storageInstanceId, URLLogFetcher urlLogFetcher,
                                  BinlogFile binlogFile,
                                  LinkedList<BinlogFile> binlogFileQueue) {
        super(0);
        this.storageInstanceId = storageInstanceId;
        this.urlLogFetcher = urlLogFetcher;
        this.binlogFile = binlogFile;
        this.binlogFileQueue = binlogFileQueue;
    }

    @Override
    public boolean fetch() throws IOException {
        boolean readData = urlLogFetcher.fetch();
        if (readData) {
            return true;
        }

        logger.info("finish read " + binlogFile + " size : " + urlLogFetcher.readSize());

        urlLogFetcher.close();
        urlLogFetcher = null;
        int idx = binlogFileQueue.indexOf(binlogFile) + 1;
        if (idx >= binlogFileQueue.size()) {
            throw new ConsumeOSSBinlogEndException();
        }
        BinlogFile nextFile = binlogFileQueue.get(idx);
        logger.info(
            "last file " + binlogFile.getLogname() + " finish , rotate to new binlog file " + nextFile.getLogname()
                + " with link : " + nextFile.getDownloadLink());

        binlogFile = nextFile;
        urlLogFetcher = new URLLogFetcher();
        urlLogFetcher.open(nextFile.getIntranetDownloadLink(), nextFile.getFileSize());
        return urlLogFetcher.fetch();
    }

    @Override
    public LogBuffer buffer() {
        return urlLogFetcher;
    }

    @Override
    public void close() throws IOException {
        if (urlLogFetcher == null) {
            return;
        }
        urlLogFetcher.close();
        urlLogFetcher = null;
    }
}
