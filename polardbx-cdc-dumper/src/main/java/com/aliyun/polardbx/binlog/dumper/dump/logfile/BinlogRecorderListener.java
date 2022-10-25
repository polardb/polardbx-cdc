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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class BinlogRecorderListener implements LogFileListener, Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BinlogRecorderListener.class);
    private final String binlogDir;
    private final String taskName;
    private final String binlogNamePrefix;
    private LinkedBlockingQueue<RecordTask> taskQueue = new LinkedBlockingQueue();
    private Thread recordThread;
    private boolean backupOn;

    public BinlogRecorderListener(String binlogDir, String taskName, String binlogNamePrefix) {
        this.binlogDir = binlogDir;
        this.taskName = taskName;
        this.binlogNamePrefix = binlogNamePrefix;
        this.backupOn = RemoteBinlogProxy.getInstance().isBackupOn();
        recordThread = new Thread(this, "record-binlog-thread");
        recordThread.setDaemon(false);
        recordThread.start();
    }

    private static String lastFileName(String name) {
        int dot = name.indexOf(".");
        String suffix = name.substring(dot + 1);
        int seq = Integer.valueOf(suffix);
        if (seq == 1) {
            return null;
        }
        String prefix = name.substring(0, dot);
        return prefix + "." + StringUtils.leftPad((seq - 1) + "", suffix.length(), '0');
    }

    @Override
    public void run() {
        if (!isLeader()) {
            return;
        }
        do {
            try {
                onStart();
            } catch (Exception e) {
                logger.error("on start failed", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    break;
                }
                continue;
            }
            break;
        } while (true);

        while (true) {
            try {
                RecordTask task = taskQueue.poll(5, TimeUnit.SECONDS);
                if (task != null) {
                    task.exec();
                }
            } catch (Throwable e) {
                logger.error("record task failed", e);
            }
        }

    }

    public void onStart() {
        //prepare local files
        File[] binlogListFiles = new File(binlogDir).listFiles((dir, name) -> name.startsWith(binlogNamePrefix));
        if (binlogListFiles == null || binlogListFiles.length == 0) {
            return;
        }
        List<File> fileList = new ArrayList<>(Arrays.asList(binlogListFiles));
        fileList.sort(Comparator.comparing(File::getName));
        int lastIdx = fileList.size() - 1;
        logger.info("remove last binlog file: " + fileList.get(lastIdx) + ", and the first file is " + fileList.get(0));
        fileList.remove(lastIdx);

        BinlogOssRecord maxPurgedRecord = getObject(BinlogOssRecordService.class).getMaxPurgedRecord();
        BinlogOssRecordMapper mapper = getObject(BinlogOssRecordMapper.class);
        for (File f : fileList) {
            Optional<BinlogOssRecord> recordOptional =
                mapper.selectOne(s -> SqlBuilder.select(BinlogOssRecordDynamicSqlSupport.binlogOssRecord.allColumns())
                    .from(BinlogOssRecordDynamicSqlSupport.binlogOssRecord)
                    .where(BinlogOssRecordDynamicSqlSupport.binlogFile, isEqualTo(f.getName())));
            if (!recordOptional.isPresent()) {
                // 小于等于maxPurgeFileName的local file就不用补偿了，因为不同容器本地文件列表不一致，继续补偿会引发一致性问题
                if (maxPurgedRecord == null || f.getName().compareTo(maxPurgedRecord.getBinlogFile()) > 0) {
                    taskQueue.add(new BinlogRecordFinishTask(f));
                }
            } else {
                BinlogOssRecord record = recordOptional.get();
                if (record.getLogSize() == 0) {
                    taskQueue.add(new BinlogRecordFinishTask(f));
                }
            }
        }
    }

    private boolean isLeader() {
        return RuntimeLeaderElector.isDumperLeader(taskName);
    }

    @Override
    public void onCreateFile(File file) {
        logger.info("on create file!" + file.getName());
        new BinlogRecordCreateTask(file).exec();
        String lastFileName = lastFileName(file.getName());
        if (StringUtils.isNotBlank(lastFileName)) {
            taskQueue.add(new BinlogRecordFinishTask(new File(binlogDir + File.separator + lastFileName)));
        }
    }

    @Override
    public void onRotateFile(File currentFile, String nextfile) {
        logger.info("on rotate file!" + currentFile.getName() + "," + nextfile);
    }

    @Override
    public void onFinishFile(File file, Long logEndTime) {
        logger.info("on finish file!" + file.getName());
        if (logEndTime != null) {
            BinlogOssRecordMapper mapper = getObject(BinlogOssRecordMapper.class);
            mapper.update(r -> r.set(BinlogOssRecordDynamicSqlSupport.logEnd).equalTo(new Date(logEndTime))
                .where(BinlogOssRecordDynamicSqlSupport.binlogFile, isEqualTo(file.getName())));
        }
    }

    @Override
    public void onDeleteFile(File file) {
        logger.info("on delete file!" + file.getName());
    }

    interface RecordTask {
        void exec() throws FileNotFoundException;
    }

    class BinlogRecordCreateTask implements RecordTask {
        private final File file;

        public BinlogRecordCreateTask(File file) {
            this.file = file;
        }

        @Override
        public void exec() {
            BinlogOssRecordMapper recordMapper = getObject(BinlogOssRecordMapper.class);
            Optional<BinlogOssRecord> recordOptional = recordMapper
                .selectOne(s -> SqlBuilder.select(BinlogOssRecordDynamicSqlSupport.binlogOssRecord.allColumns())
                    .from(BinlogOssRecordDynamicSqlSupport.binlogOssRecord)
                    .where(BinlogOssRecordDynamicSqlSupport.binlogFile, isEqualTo(file.getName())));
            if (recordOptional.isPresent()) {
                return;
            }
            BinlogOssRecord record = new BinlogOssRecord();
            if (!backupOn) {
                record.setUploadStatus(BinlogUploadStatusEnum.IGNORE.getValue());
            } else {
                record.setUploadStatus(BinlogUploadStatusEnum.CREATE.getValue());
            }
            record.setBinlogFile(file.getName());
            record.setGmtCreated(new Date());
            record.setGmtModified(new Date());
            record.setPurgeStatus(BinlogPurgeStatusEnum.UN_COMPLETE.getValue());
            record.setLogBegin(new Date());
            record.setLogSize(0L);
            recordMapper.insert(record);
        }
    }

    class BinlogRecordFinishTask implements RecordTask {
        private final File file;

        public BinlogRecordFinishTask(File file) {
            this.file = file;
        }

        @Override
        public void exec() throws FileNotFoundException {
            BinlogOssRecordMapper recordMapper = getObject(BinlogOssRecordMapper.class);
            Optional<BinlogOssRecord> recordOptional = recordMapper.selectOne(
                s -> SqlBuilder.select(BinlogOssRecordDynamicSqlSupport.binlogOssRecord.allColumns()).from(
                    BinlogOssRecordDynamicSqlSupport.binlogOssRecord)
                    .where(BinlogOssRecordDynamicSqlSupport.binlogFile, isEqualTo(file.getName())));
            int seekBufferSize = DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_BUFFER_SIZE);
            boolean useDirectByteBuffer = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER);
            BinlogFile binlogFile = new BinlogFile(file, "r", 1024, seekBufferSize, useDirectByteBuffer);
            try {
                if (recordOptional.isPresent()) {
                    BinlogOssRecord record = recordOptional.get();
                    BinlogOssRecord newRecord = new BinlogOssRecord();
                    newRecord.setLogSize(file.length());
                    newRecord.setLogBegin(new Date(binlogFile.getLogBegin()));
                    if (record.getLogEnd() == null) {
                        // onFinishFile方法会进行赋值，这里进行补偿
                        newRecord.setLogEnd(new Date(binlogFile.getLogEnd()));
                        logger.info("log end is null, will set it by seeking from file, file name "
                            + record.getBinlogFile());
                    }
                    newRecord.setGmtModified(new Date());
                    newRecord.setId(record.getId());
                    if (!backupOn) {
                        newRecord.setUploadStatus(BinlogUploadStatusEnum.IGNORE.getValue());
                    }
                    recordMapper.updateByPrimaryKeySelective(newRecord);
                } else {
                    logger.warn("can`t find binlog record info for file {} in BinlogRecordFinishTask, "
                        + "will insert instantly ", file);
                    BinlogOssRecord record = new BinlogOssRecord();
                    if (!backupOn) {
                        record.setUploadStatus(BinlogUploadStatusEnum.IGNORE.getValue());
                    } else {
                        record.setUploadStatus(BinlogUploadStatusEnum.CREATE.getValue());
                    }
                    record.setBinlogFile(file.getName());
                    record.setGmtCreated(new Date());
                    record.setGmtModified(new Date());
                    record.setPurgeStatus(BinlogPurgeStatusEnum.UN_COMPLETE.getValue());
                    record.setLogBegin(new Date(binlogFile.getLogBegin()));
                    record.setLogEnd(new Date(binlogFile.getLogEnd()));
                    record.setLogSize(file.length());
                    recordMapper.insert(record);
                }
            } finally {
                try {
                    binlogFile.close();
                } catch (IOException e) {

                }
            }
        }
    }
}
