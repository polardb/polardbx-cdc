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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.LabEventType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_BUFFER_DIRECT_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_KEY;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_BACKUP;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * 负责监听本地binlog文件的变化，将变化的binlog文件信息记录到binlog_oss_record表中
 *
 * @author chengjin, yudong
 */
public class BinlogRecordManager implements IBinlogListener, Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BinlogRecordManager.class);
    private final String binlogFullPath;
    private final String taskName;
    private final TaskType taskType;
    private final String group;
    private final String stream;
    private final String clusterId;
    private final LinkedBlockingQueue<RecordTask> taskQueue;
    private final boolean backupOn;
    private final ThreadPoolExecutor executor;
    private final BinlogOssRecordMapper recordMapper;
    private final BinlogOssRecordService recordService;

    public BinlogRecordManager(String group, String stream, String taskName, TaskType taskType, String rootPath) {
        this.recordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        this.recordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        this.binlogFullPath = BinlogFileUtil.getFullPath(rootPath, group, stream);
        this.taskName = taskName;
        this.taskType = taskType;
        this.group = group;
        this.stream = stream;
        this.clusterId = getString(CLUSTER_ID);
        this.taskQueue = new LinkedBlockingQueue<>();
        this.backupOn = RemoteBinlogProxy.getInstance().isBackupOn();

        // 工作线程，不断从taskQueue中取Task执行
        executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1), r -> {
            Thread t = new Thread(r);
            t.setName("binlog-record-manager-" + group + ":" + stream);
            t.setDaemon(false);
            return t;
        });
        executor.execute(this);
    }

    @Override
    public void run() {
        try {
            MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_BACKUP);
            if (isDumperFollower()) {
                executor.shutdown();
                return;
            }

            do {
                try {
                    doCompensation();
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
        } finally {
            MDC.remove(MDC_THREAD_LOGGER_KEY);
        }
    }

    /**
     * binlog_oss_record表补偿逻辑
     * Q:什么是补偿逻辑？
     * A:在表中插入本地存在但是表中不存在的binlog文件记录
     * Q:什么场景可能会触发补偿逻辑？
     * A:dumper发生一次主从切换，如果slave复制master binlog文件的延迟比较大，某些文件还没来得及复制，则切换之后会出现这样的场景：
     * binlog_oss_record表中有一些文件记录，但是master本地并没有这些文件，这时master会将这些记录删除
     * Q:如何进行补偿？
     * A:本地有某些binlog文件，但是binlog_oss_record表中无对应的记录，此时会尝试在表中插入这些文件对应的记录
     * 但是补偿有一个限制：如果文件的文件序号小于max purged record，说明这些文件在oss上已经被删除了，所以也没必要补偿了
     */
    public void doCompensation() {
        List<File> localFiles = BinlogFileUtil.listLocalBinlogFiles(binlogFullPath, group, stream);
        if (localFiles.isEmpty()) {
            logger.info("no local binlog files, skip compensation");
            return;
        }

        // 最后一个文件是不完整的，不进行补偿
        localFiles.remove(localFiles.size() - 1);

        Optional<BinlogOssRecord> maxPurgedRecord = recordService.getMaxPurgedRecord(group, stream, clusterId);
        for (File f : localFiles) {
            Optional<BinlogOssRecord> recordOptional =
                recordService.getRecordByName(group, stream, clusterId, f.getName());
            if (recordOptional.isPresent()) {
                if (recordOptional.get().getLogSize() == 0) {
                    logger.info("local file corresponding record log size is 0, add finish task, file name:{}",
                        f.getName());
                    taskQueue.add(new BinlogRecordFinishTask(f));
                }
            } else {
                // 本地有binlog文件，表中没有对应的记录，并且文件不属于已经被purged的文件，则进行补偿
                if (!maxPurgedRecord.isPresent() || f.getName().compareTo(maxPurgedRecord.get().getBinlogFile()) > 0) {
                    logger.info("local file corresponding record not exist, add finish task, file name:{}",
                        f.getName());
                    LabEventManager.logEvent(LabEventType.DUMPER_DO_COMPENSATION);
                    taskQueue.add(new BinlogRecordFinishTask(f));
                }
            }
        }
    }

    @Override
    public void onCreateFile(File file) {
        if (isDumperFollower()) {
            return;
        }

        new BinlogRecordCreateTask(file).exec();
        String lastFileName = BinlogFileUtil.getPrevBinlogFileName(file.getName());
        if (!StringUtils.isNotBlank(lastFileName)) {
            return;
        }
        File lastFile = new File(binlogFullPath, lastFileName);
        if (lastFile.exists()) {
            taskQueue.add(new BinlogRecordFinishTask(lastFile));
        }
    }

    @Override
    public void onRotateFile(File currentFile, String nextFile) {
    }

    @Override
    public void onFinishFile(File file, BinlogEndInfo binlogEndInfo) {
        if (isDumperFollower()) {
            return;
        }

        if (binlogEndInfo != null) {
            Optional<BinlogOssRecord> optionalRecord =
                recordService.getRecordByName(group, stream, clusterId, file.getName());
            if (!optionalRecord.isPresent()) {
                throw new RuntimeException("want to update record but record not exist, file name:" + file.getName());
            }
            BinlogOssRecord record = optionalRecord.get();
            record.setLogEnd(new Date(binlogEndInfo.getLastEventTimeStamp()));
            record.setLastTso(binlogEndInfo.getLastEventTso());
            record.setLogSize(file.length());
            if (!backupOn) {
                // 如果不开启备份，上传状态为IGNORE表示该文件是完整的
                record.setUploadStatus(BinlogUploadStatus.IGNORE.getValue());
            }
            logger.info("on finish file, update binlog_oss_record table, file name:{}, record {}",
                record.getBinlogFile(), record);
            recordMapper.updateByPrimaryKeySelective(record);
        }
    }

    @Override
    public void onDeleteFile(File file) {
    }

    private boolean isDumperFollower() {
        return CommonUtils.isGlobalBinlog(group, stream) && !RuntimeLeaderElector.isDumperLeader(taskName);
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
            Optional<BinlogOssRecord> recordOptional =
                recordService.getRecordByName(group, stream, clusterId, file.getName());
            if (recordOptional.isPresent()) {
                logger.info(
                    "file corresponding record already exist, skip insert binlog_oss_record table, file name:{}",
                    file.getName());
                return;
            }
            BinlogOssRecord record = new BinlogOssRecord();
            record.setBinlogFile(file.getName());
            record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
            record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
            record.setLogBegin(new Date());
            record.setLogSize(0L);
            record.setGroupId(group);
            record.setStreamId(stream);
            record.setClusterId(clusterId);
            logger.info("insert binlog_oss_record table, file name:{}, record:{}", record.getBinlogFile(), record);
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
            Optional<BinlogOssRecord> recordOptional =
                recordService.getRecordByName(group, stream, clusterId, file.getName());

            int seekBufferSize = DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_BUFFER_SIZE);
            boolean useDirectByteBuffer = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_BUFFER_DIRECT_ENABLE);
            BinlogFile binlogFile = new BinlogFile(file, "r", 1024, seekBufferSize, useDirectByteBuffer, null);
            try {
                if (recordOptional.isPresent()) {
                    BinlogOssRecord record = recordOptional.get();
                    record.setLogSize(file.length());
                    record.setLogBegin(new Date(binlogFile.getLogBegin()));
                    if (record.getLogEnd() == null) {
                        // onFinishFile方法会进行赋值，这里进行补偿
                        BinlogEndInfo binlogEndInfo = binlogFile.getLogEndInfo();
                        record.setLogEnd(new Date(binlogEndInfo.getLastEventTimeStamp()));
                        record.setLastTso(binlogEndInfo.getLastEventTso());
                        logger.info("log end is null, will set it by seeking from file, file name "
                            + record.getBinlogFile());
                    }
                    if (!backupOn) {
                        record.setUploadStatus(BinlogUploadStatus.IGNORE.getValue());
                    }
                    logger.info("update binlog_oss_record table, file name:{}, record {}", record.getBinlogFile(),
                        record);
                    recordMapper.updateByPrimaryKeySelective(record);
                } else {
                    logger.warn("file corresponding record does not exist, will insert, file name:{}", file.getName());
                    BinlogOssRecord record = new BinlogOssRecord();
                    if (!backupOn) {
                        record.setUploadStatus(BinlogUploadStatus.IGNORE.getValue());
                    } else {
                        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
                    }
                    BinlogEndInfo binlogEndInfo = binlogFile.getLogEndInfo();
                    record.setBinlogFile(file.getName());
                    record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
                    record.setLogBegin(new Date(binlogFile.getLogBegin()));
                    record.setLogEnd(new Date(binlogEndInfo.getLastEventTimeStamp()));
                    record.setLogSize(file.length());
                    record.setLastTso(binlogEndInfo.getLastEventTso());
                    record.setGroupId(group);
                    record.setStreamId(stream);
                    record.setClusterId(clusterId);
                    logger.info("insert binlog_oss_record table, file name:{}, record:{}", record.getBinlogFile(),
                        record);
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
