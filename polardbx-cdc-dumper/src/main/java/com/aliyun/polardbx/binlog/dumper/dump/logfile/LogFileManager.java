/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.ICursorProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class LogFileManager implements ICursorProvider {

    private static final String BINLOG_FILE_PREFIX = "binlog.";
    private static final int BINLOG_FILE_SUFFIX_LENGTH = 6;        // TODO binlog file name的数字编号是可以大于6位的
    private static final Logger logger = LoggerFactory.getLogger(LogFileManager.class);

    private String taskName;
    private String binlogFileDirPath;
    private File binlogFileDir;
    private Integer binlogFileSize;
    private boolean dryRun;
    private FlushPolicy flushPolicy;
    private int flushInterval;
    private int writeBufferSize;
    private int seekBufferSize;
    private LogFileGenerator logFileGenerator;
    private LogFileCopier logFileCopier;
    private volatile Cursor latestFileCursor;
    private volatile boolean running;
    private LogFileListenerWrapper wrapper = new LogFileListenerWrapper();

    public void start() {
        if (running) {
            return;
        }
        running = true;

        try {
            tryCreateBinlogDir();
            this.wrapper
                .addLogFileListener(new BinlogRecorderListener(binlogFileDirPath, taskName, BINLOG_FILE_PREFIX));
            if (RuntimeLeaderElector.isDumperLeader(taskName)) {
                logFileGenerator = new LogFileGenerator(this,
                    binlogFileSize,
                    dryRun,
                    flushPolicy,
                    flushInterval,
                    writeBufferSize,
                    seekBufferSize);
                logFileGenerator.start();
            } else {
                logFileCopier = new LogFileCopier(this, writeBufferSize, seekBufferSize);
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

    public String getMinBinlogFileName() {
        if (!binlogFileDir.exists()) {
            return "";
        }

        Optional<String> minFileName = Arrays.stream(Objects.requireNonNull(binlogFileDir.list((dir, name) ->
            StringUtils.startsWith(name, BINLOG_FILE_PREFIX) && StringUtils.isNumericSpace(StringUtils.split(name,
                ".")[1])))).min(String::compareTo);
        return minFileName.orElse("");
    }

    public String getMinBinlogFile() {
        String minLogFileName = getMinBinlogFileName();
        if (StringUtils.isNotBlank(minLogFileName)) {
            return new File(binlogFileDirPath + "/") + minLogFileName;
        }
        return null;
    }

    /**
     * 获取最大编号的binlog文件名称（不带路径前缀）
     */
    public String getMaxBinlogFileName() {
        if (!binlogFileDir.exists()) {
            return "";
        }

        Optional<String> minFileName = Arrays
            .stream(Objects
                .requireNonNull(binlogFileDir.list(
                    (dir, name) -> StringUtils.startsWith(name, BINLOG_FILE_PREFIX) && StringUtils
                        .isNumericSpace(StringUtils.split(name, ".")[1]))))
            .max(String::compareTo);
        return minFileName.orElse("");
    }

    public File getMaxBinlogFile() {
        String maxLogFileName = getMaxBinlogFileName();
        if (StringUtils.isNotBlank(maxLogFileName)) {
            return new File(binlogFileDirPath + "/" + maxLogFileName);
        }
        return null;
    }

    /**
     * 按照文件后缀编号顺序(由小到大)返回文件名称列表
     */
    public List<String> getAllLogFileNamesOrdered() {
        if (!binlogFileDir.exists()) {
            return new ArrayList<>();
        }

        return Arrays
            .stream(Objects
                .requireNonNull(binlogFileDir.list(
                    (dir, name) -> StringUtils.startsWith(name, BINLOG_FILE_PREFIX) && StringUtils
                        .isNumericSpace(StringUtils.split(name, ".")[1]))))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * 按照文件后缀编号顺序(由小到大)返回文件列表
     */
    public List<File> getAllLogFilesOrdered() {
        List<String> list = getAllLogFileNamesOrdered();
        return list.stream().map(s -> new File(binlogFileDirPath + "/" + s)).collect(Collectors.toList());
    }

    /**
     * 对某个文件进行删除重建，这个文件一定是编号最大的那个文件
     */
    public File reCreateFile(File file) throws IOException {
        String maxFileName = getMaxBinlogFileName();
        String filePath = file.getAbsolutePath();
        if (!StringUtils.equals(file.getName(), maxFileName)) {
            throw new PolardbxException(
                String.format("File [%s] is not the max binlog file, can't be recreate.", file.getName()));
        }
        FileUtils.forceDelete(file);
        wrapper.onDeleteFile(file);
        return createFile(filePath);
    }

    public File createFirstLogFile() {
        if (!getAllLogFileNamesOrdered().isEmpty()) {
            throw new PolardbxException("log files are already exists in binlog directory, can`t do first create.");
        }
        return createFile(binlogFileDirPath + "/" + BINLOG_FILE_PREFIX + "000001");
    }

    public File rotateFile(File file) {
        String maxFileName = getMaxBinlogFileName();
        if (!StringUtils.equals(file.getName(), maxFileName)) {
            throw new IllegalArgumentException(
                "Rotating file is not the max file, rotating file is " + file.getName() + ", max file is "
                    + maxFileName);
        }
        wrapper.onFinishFile(file);
        String nextBinlogFile = nextBinlogFileName(file.getName());
        wrapper.onRotateFile(file, nextBinlogFile);
        File newFile = createFile(binlogFileDirPath + "/" + nextBinlogFile);
        return newFile;
    }

    public String getFullName(String fileName) {
        return binlogFileDirPath + "/" + fileName;
    }

    private void tryCreateBinlogDir() {
        if (!binlogFileDir.exists()) {
            binlogFileDir.mkdirs();
            if (!binlogFileDir.exists()) {
                throw new PolardbxException("error to create binlog root dir : " + binlogFileDirPath);
            }
            binlogFileDir = new File(binlogFileDirPath);
        }
    }

    public String nextBinlogFileName(String fileName) {
        String suffix = fileName.split("\\.")[1];
        int suffixNbr = Integer.parseInt(suffix);
        String newSuffix = String.format("%0" + BINLOG_FILE_SUFFIX_LENGTH + "d", ++suffixNbr);
        return BINLOG_FILE_PREFIX + newSuffix;
    }

    public File createFile(String fileName) {
        File file = new File(fileName);
        return createFile(file);
    }

    public File createFile(File file) {
        try {
            boolean result = file.createNewFile();
            assert result;
            wrapper.onCreateFile(file);
            return file;
        } catch (IOException e) {
            throw new PolardbxException("binlog file create failed, file name is " + file.getAbsolutePath(), e);
        }
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getBinlogFileDirPath() {
        return binlogFileDirPath;
    }

    public void setBinlogFileDirPath(String binlogFileDirPath) {
        this.binlogFileDirPath = binlogFileDirPath;
        this.binlogFileDir = new File(binlogFileDirPath);
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

    public int getSeekBufferSize() {
        return seekBufferSize;
    }

    public void setSeekBufferSize(int seekBufferSize) {
        this.seekBufferSize = seekBufferSize;
    }

    @Override
    public Cursor getLatestFileCursor() {
        return latestFileCursor;
    }

    public void setLatestFileCursor(Cursor latestFileCursor) {
        this.latestFileCursor = latestFileCursor;
    }

}
