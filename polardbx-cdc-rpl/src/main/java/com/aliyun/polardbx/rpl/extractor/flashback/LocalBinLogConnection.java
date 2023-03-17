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
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.PreviousGtidsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.ServerIdNotMatchException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author ziyang.lb
 */
@Slf4j
public class LocalBinLogConnection implements ErosaConnection {

    private static final int BUFFER_SIZE = 16 * 1024;
    /**
     * Wait for next file in binlogFileQueue to be ready or return null directly.
     */
    private final boolean needWait;
    /**
     * Specify the local directory path.
     */
    private final String localDirectory;
    /**
     * After each file processing ends, the onFinishFile method will be called,
     * and after all files in the queue are processed, the onEnd method will be called.
     */
    private final ILocalBinlogEventListener eventListener;
    /**
     * All the files need to be processed.
     */
    private final List<String> binlogList;
    private final long currentServerId;
    /**
     * LogDecoder(LogContext) need this.
     */
    private ServerCharactorSet serverCharactorSet;
    private IBinlogFileQueue binlogFileQueue = null;
    private boolean running = false;
    private String latestFileName;

    private long latestTimestamp;

    private long latestServerId;

    public LocalBinLogConnection(String localDirectory, List<String> binlogList, boolean needWait,
                                 ILocalBinlogEventListener eventListener, long currentServerId) {
        this.localDirectory = localDirectory;
        this.binlogList = binlogList;
        this.needWait = needWait;
        this.eventListener = eventListener;
        this.currentServerId = currentServerId;
    }

    @Override
    public void connect() throws IOException {
        if (this.binlogFileQueue == null) {
            this.binlogFileQueue = new BinlogFileQueue(this.localDirectory);
            if (!CollectionUtils.isEmpty(binlogList)) {
                ((BinlogFileQueue) binlogFileQueue).setBinlogList(binlogList);
            }
        }
        this.running = true;
    }

    @Override
    public void reconnect() throws IOException {
        disconnect();
        connect();
    }

    @Override
    public void disconnect() {
        this.running = false;
        if (this.binlogFileQueue != null) {
            this.binlogFileQueue.destroy();
        }
        this.binlogFileQueue = null;
        this.running = false;
    }

    @Override
    public void dump(String binlogFileName, Long binlogPosition, Long startTimestampMills, SinkFunction func)
        throws IOException, TableIdNotFoundException {
        File current = new File(localDirectory, binlogFileName);

        FileLogFetcher fetcher = new FileLogFetcher(BUFFER_SIZE);
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext context = new LogContext();
        context.setServerCharactorSet(serverCharactorSet);
        boolean isLegal = false;
        boolean mindTimestamp = true;
        if (startTimestampMills == null || startTimestampMills == -1) {
            mindTimestamp = false;
        }
        try {
            checkBinlogFileAvailable(current);
            fetcher.open(current, binlogPosition);
            context.setLogPosition(new LogPosition(binlogFileName, binlogPosition));
            while (running) {
                boolean needContinue = true;
                LogEvent event;
                // 处理一下binlog文件名
                event = new RotateLogEvent(context.getLogPosition().getFileName(), 4);
                func.sink(event, context.getLogPosition());
                while (fetcher.fetch()) {
                    event = decoder.decode(fetcher, context);
                    if (event == null) {
                        continue;
                    }
                    if (event instanceof PreviousGtidsLogEvent) {
                        if (currentServerId != -1 && currentServerId != event.getServerId()) {
                            log.error(
                                "need serverId " + currentServerId + ", but receive serverId " + event.getServerId()
                                    + " now binlog file name : " + context.getLogPosition().getFileName());
                            throw new ServerIdNotMatchException(
                                "need serverId " + currentServerId + ", but receive serverId " + event.getServerId());
                        } else {
                            log.info(
                                "find binlog with proper server id " + currentServerId + " " + event.getServerId());
                        }
                    }
                    if (mindTimestamp) {
                        if (event.getWhen() > startTimestampMills) {
                            if (!isLegal) {
                                throw new PolardbxException(
                                    "can not find position from binlog file need timestamp is : " + startTimestampMills
                                        + "s, binlog position timestamp is " + event.getWhen());
                            }
                        } else {
                            isLegal = true;
                        }
                        if (event.getWhen() < startTimestampMills) {
                            continue;
                        }
                        mindTimestamp = false;
                    }
                    if (!func.sink(event, context.getLogPosition())) {
                        needContinue = false;
                        break;
                    }
                    latestServerId = event.getServerId();
                    latestTimestamp = event.getWhen();
                }
                latestFileName = context.getLogPosition().getFileName();
                fetcher.close(); // 关闭上一个文件
                if (eventListener != null) {
                    eventListener.onFinishFile(current,
                        new BinlogPosition(latestFileName, 0, latestServerId, latestTimestamp));
                }
                if (needContinue) {// 读取下一个
                    File nextFile;
                    if (needWait) {
                        nextFile = binlogFileQueue.waitForNextFile(current);
                    } else {
                        nextFile = binlogFileQueue.getNextFile(current);
                    }

                    if (nextFile == null) {
                        break;
                    }

                    current = nextFile;
                    checkBinlogFileAvailable(current);
                    fetcher.open(current);
                    context.setLogPosition(new LogPosition(nextFile.getName()));
                } else {
                    break;// 跳出
                }
            }

            if (eventListener != null) {
                eventListener.onEnd();
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            running = false;
            if (fetcher != null) {
                fetcher.close();
            }
        }
    }

    private void checkBinlogFileAvailable(File file) {
        while (!file.exists()) {
            log.warn("wait for binlog file : " + file.getName() + " available! will sleep 10s ....");
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                log.error("interrupted exception.");
            }
        }
    }

    public File firstBinlogFile() {
        return binlogFileQueue.getFirstFile();
    }

    public void setServerCharactorSet(ServerCharactorSet serverCharactorSet) {
        this.serverCharactorSet = serverCharactorSet;
    }

    @Override
    public void seek(String binlogFileName, Long binlogPosition, SinkFunction func) {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public ErosaConnection fork() {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public void dump(long timestamp, SinkFunction func) throws Exception {
        throw new PolardbxException("not support");
    }

    @Override
    public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search) throws IOException {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public BinlogPosition findEndPosition(Long tso) {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public long binlogFileSize(String searchFileName) throws IOException {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public String preFileName(String currentFileName) {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public void dump(GTIDSet gtidSet, SinkFunction func) throws IOException {
        throw new NullPointerException("Not implement yet");
    }
}
