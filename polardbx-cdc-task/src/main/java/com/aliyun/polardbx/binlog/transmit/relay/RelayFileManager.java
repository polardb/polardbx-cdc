/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_USE_DIRECT_MEM;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayFileManager {
    private static final String RELAY_FILE_PREFIX = "relay.";
    private static final int RELAY_FILE_SUFFIX_LENGTH = 10;
    private static final long MAX_SUFFIX_NUM = 9999999999L;
    private final String bathPath;
    private File baseFileDirectory;

    public RelayFileManager(String bathPath) {
        this.bathPath = bathPath;
    }

    public void init() {
        try {
            FileUtils.forceMkdir(new File(bathPath));
            baseFileDirectory = new File(bathPath);
        } catch (IOException e) {
            throw new PolardbxException("relay file store engine open error!", e);
        }
    }

    public File createFirstRelayFile() {
        File file = new File(bathPath + "/" + getFileName(1));
        try {
            if (file.createNewFile()) {
                return file;
            }
            throw new PolardbxException("create first relay file failed.");
        } catch (IOException e) {
            throw new PolardbxException("create first relay file failed.", e);
        }
    }

    public RelayFile openAndSeekRelayFile(String fileName, long filePos) {
        try {
            File file = new File(bathPath + "/" + fileName);
            RelayFile relayFile = new RelayFile(file, getInt(BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_SIZE),
                getBoolean(BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_USE_DIRECT_MEM));
            relayFile.seekTo(filePos);
            relayFile.tryTruncate();
            cleanRelayFilesAfter(file);
            return relayFile;
        } catch (IOException e) {
            throw new PolardbxException(String.format("open and seek relay file failed, %s:%s!", fileName, filePos), e);
        }
    }

    public RelayFile rotateRelayFile(File preFile) {
        try {
            String preFileName = preFile.getName();
            File nextFile = new File(bathPath + "/" + nextFileName(preFileName));
            return new RelayFile(nextFile, getInt(BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_SIZE),
                getBoolean(BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_USE_DIRECT_MEM));
        } catch (FileNotFoundException e) {
            throw new PolardbxException("rotate relay file failed , " + preFile.getName(), e);
        }
    }

    public File getFile(String fileName) {
        return new File(bathPath + "/" + fileName);
    }

    public String nextFileName(String preFileName) {
        long preFileNum = Long.parseLong(preFileName.split("\\.")[1]);
        return getFileName(++preFileNum);
    }

    public boolean isEmpty() {
        return listRelayFiles().isEmpty();
    }

    public void cleanAllRelayFiles() {
        listRelayFiles().forEach(this::deleteFile);
    }

    public void cleanRelayFilesAfter(File file) {
        List<File> allRelayFiles = listRelayFiles();
        allRelayFiles.forEach(f -> {
            if (f.getName().compareTo(file.getName()) > 0) {
                deleteFile(f);
            }
        });
    }

    public void cleanRelayFilesBefore(String fileName) {
        List<File> allRelayFiles = listRelayFiles();
        allRelayFiles.forEach(f -> {
            if (f.getName().compareTo(fileName) < 0) {
                deleteFile(f);
                log.info("clean relay file:{}", f.getName());
            }
        });
    }

    public List<File> listRelayFiles() {
        List<File> result = new ArrayList<>();
        File[] files = baseFileDirectory.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(RELAY_FILE_PREFIX)) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public String getMaxFileName() {
        return RELAY_FILE_PREFIX + MAX_SUFFIX_NUM;
    }

    private void deleteFile(File file) {
        if (!file.delete()) {
            throw new PolardbxException("delete relay file error , " + file.getName());
        }
    }

    private String getFileName(long index) {
        return RELAY_FILE_PREFIX + StringUtils.leftPad(index + "", RELAY_FILE_SUFFIX_LENGTH, "0");
    }
}
