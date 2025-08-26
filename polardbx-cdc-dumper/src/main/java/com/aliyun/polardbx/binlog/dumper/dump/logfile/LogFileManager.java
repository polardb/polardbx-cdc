/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.lock.LogFileLockManager;
import com.aliyun.polardbx.binlog.restore.BinlogRestoreManager;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.task.IDumperStatisticProvider;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.enums.BinlogUploadStatus.SUCCESS;

/**
 * Created by ziyang.lb
 **/
@Setter
@Getter
@Slf4j
public class LogFileManager implements IDumperStatisticProvider {
    private String taskName;
    private TaskType taskType;
    private ExecutionConfig executionConfig;
    private String binlogRootPath;
    private Integer binlogFileSize;
    private boolean dryRun;
    private FlushPolicy flushPolicy;
    private int flushInterval;
    private int writeBufferSize;
    private String groupName;
    private String streamName;
    private LogFileGenerator logFileGenerator;
    private LogFileCopier logFileCopier;
    private volatile BinlogCursor latestFileCursor;
    private CdcFileSystem cdcFileSystem;
    private BinlogListenerWrapper binlogListeners;
    private volatile boolean running;
    private LogFileLockManager logFileLockManager;
    private BinlogOssRecordService binlogOssRecordService;

    public void start() {
        if (running) {
            return;
        }
        running = true;

        try {
            cdcFileSystem = new CdcFileSystem(binlogRootPath, groupName, streamName);
            binlogListeners = new BinlogListenerWrapper();
            binlogListeners.addListener(
                new BinlogRecordManager(executionConfig.getRuntimeVersion(), groupName, streamName, taskName, taskType,
                    binlogRootPath));
            binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);

            if (RuntimeLeaderElector.isDumperMasterOrX(executionConfig.getRuntimeVersion(), taskType, taskName)) {
                if (isForceRecover(executionConfig)) {
                    String fullPath = BinlogFileUtil.getFullPath(binlogRootPath, groupName, streamName);
                    deleteLocalBinlogFilesIfUploaded(fullPath, groupName, streamName, getString(CLUSTER_ID));
                } else {
                    if (isForceDownload(executionConfig)) {
                        String fullPath = BinlogFileUtil.getFullPath(binlogRootPath, groupName, streamName);
                        deleteLocalBinlogFilesIfUploaded(fullPath, groupName, streamName, getString(CLUSTER_ID));
                    }
                    BinlogRestoreManager restoreManager =
                        new BinlogRestoreManager(groupName, streamName, binlogRootPath);
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
                    String fullPath = BinlogFileUtil.getFullPath(binlogRootPath, groupName, streamName);
                    deleteLocalBinlogFilesIfUploaded(fullPath, groupName, streamName, getString(CLUSTER_ID));
                }

                DumperSlaveStartMode startMode = DumperSlaveStartMode.typeOf(
                    DynamicApplicationConfig.getString(ConfigKeys.BINLOG_RECOVER_MODE_WITH_DUMPER_SLAVE));
                if (startMode == DumperSlaveStartMode.RANDOM) {
                    startMode = new Random().nextBoolean() ? DumperSlaveStartMode.DOWNLOAD : DumperSlaveStartMode.SYNC;
                }

                log.info("dumper slave start mode:{}", startMode.name());
                if (startMode == DumperSlaveStartMode.DOWNLOAD) {
                    BinlogRestoreManager restoreManager =
                        new BinlogRestoreManager(groupName, streamName, binlogRootPath);
                    restoreManager.start();
                }
                logFileCopier = new LogFileCopier(this, writeBufferSize,
                    DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_BUFFER_SIZE), executionConfig);
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
        return cdcFileSystem.getLocalBinlogFile(fileName);
    }

    public CdcFile getMinBinlogFile(boolean needOssRecord) {
        return cdcFileSystem.getMinFile(needOssRecord);
    }

    public CdcFile getMaxBinlogFile(boolean needOssRecord) {
        return cdcFileSystem.getMaxFile(needOssRecord);
    }

    /**
     * @param needOssRecord 是否需要返回更详细的oss记录信息
     */
    public CdcFile getLocalMaxBinlogFile(boolean needOssRecord) {
        return cdcFileSystem.getLocalMaxFile(needOssRecord);
    }

    public List<CdcFile> getAllBinlogFilesOrdered(boolean needOssRecord) {
        return cdcFileSystem.listAllFiles(needOssRecord);
    }

    public List<CdcFile> getAllLocalBinlogFilesOrdered(boolean needOssRecord) {
        return cdcFileSystem.listLocalFiles(needOssRecord);
    }

    private String getLocalMaxBinlogFileName() {
        CdcFile maxFile = cdcFileSystem.getLocalMaxFile(false);
        return maxFile == null ? "" : maxFile.getName();
    }

    public List<String> getAllLocalBinlogFileNamesOrdered() {
        List<String> res = new ArrayList<>();
        List<CdcFile> files = getAllLocalBinlogFilesOrdered(false);
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
        binlogListeners.onDeleteFile(file);
        return createLocalFile(fileName);
    }

    public int parseFileNumber(String fileName) {
        String suffix = fileName.split("\\.")[1];
        return Integer.parseInt(suffix);
    }

    public File rotateFile(File file, BinlogEndInfo binlogEndInfo) {
        String maxFileName = getLocalMaxBinlogFileName();
        if (!StringUtils.equals(file.getName(), maxFileName)) {
            throw new IllegalArgumentException(
                "Rotating file is not the max file, rotating file is " + file.getName() + ", max file is "
                    + maxFileName);
        }
        binlogListeners.onFinishFile(file, binlogEndInfo);
        String nextBinlogFile = BinlogFileUtil.getNextBinlogFileName(file.getName());
        binlogListeners.onRotateFile(file, nextBinlogFile);
        return createLocalFile(nextBinlogFile);
    }

    public String getFullNameOfLocalBinlogFile(String fileName) {
        return cdcFileSystem.getLocalFullName(fileName);
    }

    public File createLocalFile(String fileName) {
        File file = cdcFileSystem.newLocalFile(fileName);
        return createLocalFileHelper(file);
    }

    public File createLocalFileHelper(File file) {
        try {
            file.createNewFile();
            binlogListeners.onCreateFile(file);
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
        int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS);
        if (taskConfig.isForceRecover()
            && System.currentTimeMillis() - taskConfig.getTimestamp() < heartbeatTimeout * 6) {
            log.info(
                "will clean local binlog by force recover, with task config " + JSONObject.toJSONString(taskConfig));
            return true;
        }
        return false;
    }

    private boolean isForceDownload(ExecutionConfig taskConfig) {
        int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS);
        if (taskConfig.isForceDownload()
            && System.currentTimeMillis() - taskConfig.getTimestamp() < heartbeatTimeout * 6) {
            log.info(
                "will clean local binlog by force download, with task config " + JSONObject.toJSONString(taskConfig));
            return true;
        }
        return false;
    }

    public String getBinlogFullPath() {
        return BinlogFileUtil.getFullPath(binlogRootPath, groupName, streamName);
    }

    @Override
    public long getDumperDelay() {
        if (logFileCopier != null) {
            return logFileCopier.getDelay();
        }
        if (logFileGenerator != null) {
            return logFileGenerator.getDelay();
        }
        return Long.MAX_VALUE;
    }

    public long getLastEventTimestamp() {
        if (logFileCopier != null) {
            return logFileCopier.getLastEventTimeStampSecond();
        }
        if (logFileGenerator != null) {
            return logFileGenerator.getCurrentTsoTimeSecond();
        }
        return 0;
    }

    public void deleteLocalBinlogFilesIfUploaded(String fullPath, String gid, String sid, String cid) {
        File dir = new File(fullPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            Optional<BinlogOssRecord> recordOpt = binlogOssRecordService.getRecordByName(gid, sid, cid, fileName);
            if (recordOpt.isPresent() && recordOpt.get().getUploadStatus() == SUCCESS.getValue()) {
                file.delete();
            }
        }
    }
}
