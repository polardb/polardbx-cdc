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

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.restore.BinlogRestoreManager;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.task.ICursorProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class LogFileManager implements ICursorProvider {
    private final LogFileListenerWrapper logFileListenerWrapper = new LogFileListenerWrapper();
    private String taskName;
    private TaskType taskType;
    private ExecutionConfig executionConfig;
    private String binlogFullPath;
    private Integer binlogFileSize;
    private boolean dryRun;
    private FlushPolicy flushPolicy;
    private int flushInterval;
    private int writeBufferSize;
    private String groupName;
    private String streamName;
    private LogFileGenerator logFileGenerator;
    private LogFileCopier logFileCopier;
    private volatile Cursor latestFileCursor;
    private CdcFileSystem cdcFileSystem;
    private volatile boolean running;

    public void start() {
        if (running) {
            return;
        }
        running = true;

        try {
            cdcFileSystem = new CdcFileSystem(binlogFullPath, groupName, streamName);

            logFileListenerWrapper.addLogFileListener(
                new BinlogRecordListener(binlogFullPath, taskName, taskType, groupName, streamName));

            if (taskType == TaskType.DumperX || RuntimeLeaderElector.isDumperLeader(taskName)) {
                if (isForceRecover(executionConfig)) {
                    BinlogFileUtil.deleteBinlogFiles(binlogFullPath);
                } else {
                    if (isForceDownload(executionConfig)) {
                        BinlogFileUtil.deleteBinlogFiles(binlogFullPath);
                    }
                    BinlogRestoreManager restoreManager =
                        new BinlogRestoreManager(groupName, streamName, binlogFullPath);
                    restoreManager.start();
                }

                logFileGenerator = new LogFileGenerator(this,
                    binlogFileSize,
                    dryRun,
                    flushPolicy,
                    flushInterval,
                    writeBufferSize,
                    taskName,
                    taskType,
                    groupName,
                    streamName,
                    executionConfig);
                logFileGenerator.start();
            } else {
                // 主备Dumper的删除操作要一致
                if (isForceRecover(executionConfig) || isForceDownload(executionConfig)) {
                    BinlogFileUtil.deleteBinlogFiles(binlogFullPath);
                }

                DumperSlaveStartMode startMode = DumperSlaveStartMode.typeOf(
                    DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMPER_SLAVE_START_MODE));
                if (startMode == DumperSlaveStartMode.RANDOM) {
                    startMode = new Random().nextBoolean() ? DumperSlaveStartMode.DOWNLOAD : DumperSlaveStartMode.SYNC;
                }

                log.info("dumper slave start mode:{}", startMode.name());
                if (startMode == DumperSlaveStartMode.DOWNLOAD) {
                    BinlogRestoreManager restoreManager =
                        new BinlogRestoreManager(groupName, streamName, binlogFullPath);
                    restoreManager.start();
                }
                logFileCopier = new LogFileCopier(this, writeBufferSize,
                    DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_BUFFER_SIZE));
                logFileCopier.start();
            }
        } catch (Throwable t) {
            throw new PolardbxException("log file manager start failed.", t);
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (logFileCopier != null) {
            logFileCopier.stop();
        }
        if (logFileGenerator != null) {
            logFileGenerator.stop();
        }
    }

    public CdcFile getBinlogFileByName(String fileName) {
        return cdcFileSystem.getBinlogFile(fileName);
    }

    public CdcFile getLocalBinlogFileByName(String fileName) {
        return cdcFileSystem.getLocalFile(fileName);
    }

    public CdcFile getMinBinlogFile() {
        return cdcFileSystem.getMinFile();
    }

    public CdcFile getLocalMinBinlogFile() {
        return cdcFileSystem.getLocalMinFile();
    }

    public CdcFile getMaxBinlogFile() {
        return cdcFileSystem.getMaxFile();
    }

    public CdcFile getLocalMaxBinlogFile() {
        return cdcFileSystem.getLocalMaxFile();
    }

    public List<CdcFile> getAllBinlogFilesOrdered() {
        return cdcFileSystem.listBinlogFilesOrdered();
    }

    public List<CdcFile> getAllLocalBinlogFilesOrdered() {
        return cdcFileSystem.listLocalFiles();
    }

    private String getLocalMaxBinlogFileName() {
        CdcFile maxFile = cdcFileSystem.getLocalMaxFile();
        return maxFile == null ? "" : maxFile.getName();
    }

    public List<String> getAllLocalBinlogFileNamesOrdered() {
        List<String> res = new ArrayList<>();
        List<CdcFile> files = getAllLocalBinlogFilesOrdered();
        for (CdcFile file : files) {
            res.add(file.getName());
        }
        return res;
    }

    /**
     * 对某个文件进行删除重建，这个文件一定是编号最大的那个文件
     */
    public File recreateLocalFile(File file) throws IOException {
        String maxFileName = getLocalMaxBinlogFileName();
        String fileName = file.getName();
        if (!StringUtils.equals(file.getName(), maxFileName)) {
            throw new PolardbxException(
                String.format("File [%s] is not the max binlog file, can't be recreate.", file.getName()));
        }
        FileUtils.forceDelete(file);
        logFileListenerWrapper.onDeleteFile(file);
        return createLocalFile(fileName);
    }

    public int parseFileNumber(String fileName) {
        String suffix = fileName.split("\\.")[1];
        return Integer.parseInt(suffix);
    }

    public File rotateFile(File file, LogEndInfo logEndInfo) {
        String maxFileName = getLocalMaxBinlogFileName();
        if (!StringUtils.equals(file.getName(), maxFileName)) {
            throw new IllegalArgumentException(
                "Rotating file is not the max file, rotating file is " + file.getName() + ", max file is "
                    + maxFileName);
        }
        logFileListenerWrapper.onFinishFile(file, logEndInfo);
        String nextBinlogFile = BinlogFileUtil.getNextBinlogFileName(file.getName());
        logFileListenerWrapper.onRotateFile(file, nextBinlogFile);
        return createLocalFile(nextBinlogFile);
    }

    public String getFullNameOfLocalBinlogFile(String fileName) {
        return cdcFileSystem.getLocalFullName(fileName);
    }

    public File createLocalFile(String fileName) {
        CdcFile cdcFile = cdcFileSystem.createLocalFile(fileName);
        File file = cdcFile.newFile();
        return createLocalFileHelper(file);
    }

    public File createLocalFileHelper(File file) {
        try {
            file.createNewFile();
            logFileListenerWrapper.onCreateFile(file);
            return file;
        } catch (IOException e) {
            throw new PolardbxException("binlog file create failed, file name is " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 用于测试recover tso功能
     * 如果需要测试recover tso，Daemon会随机生成一个boolean值，放入Dumper taskConfig的forceRecover字段
     * 该方法会读取forceRecover字段的值，如果为true，则将本地binlog文件清空，便于后续测试通过recover tso产生binlog
     */
    private boolean isForceRecover(ExecutionConfig taskConfig) {
        int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS);
        if (taskConfig.isForceRecover()
            && System.currentTimeMillis() - taskConfig.getTimestamp() < heartbeatTimeout * 6) {
            log.info(
                "will clean local binlog by force recover, with task config " + JSONObject.toJSONString(taskConfig));
            return true;
        }
        return false;
    }

    private boolean isForceDownload(ExecutionConfig taskConfig) {
        int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS);
        if (taskConfig.isForceDownload()
            && System.currentTimeMillis() - taskConfig.getTimestamp() < heartbeatTimeout * 6) {
            log.info(
                "will clean local binlog by force download, with task config " + JSONObject.toJSONString(taskConfig));
            return true;
        }
        return false;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public ExecutionConfig getExecutionConfig() {
        return executionConfig;
    }

    public void setExecutionConfig(ExecutionConfig executionConfig) {
        this.executionConfig = executionConfig;
    }

    public String getLocalBinlogPath() {
        return binlogFullPath;
    }

    public void setBinlogFullPath(String localPath) {
        this.binlogFullPath = localPath;
    }

    public Integer getBinlogFileSize() {
        return binlogFileSize;
    }

    public void setBinlogFileSize(Integer binlogFileSize) {
        this.binlogFileSize = binlogFileSize;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public FlushPolicy getFlushPolicy() {
        return flushPolicy;
    }

    public void setFlushPolicy(FlushPolicy flushPolicy) {
        this.flushPolicy = flushPolicy;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public Cursor getLatestFileCursor() {
        return latestFileCursor;
    }

    public void setLatestFileCursor(Cursor latestFileCursor) {
        this.latestFileCursor = latestFileCursor;
    }

}
