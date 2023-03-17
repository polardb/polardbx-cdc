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

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_DIRECT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationVersionConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationVersionConfig.getInt;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayFileManager {
    private final static String RELAY_FILE_PREFIX = "relay.";
    private final static int RELAY_FILE_SUFFIX_LENGTH = 10;
    private final static long MAX_SUFFIX_NUM = 9999999999L;
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
            RelayFile relayFile = new RelayFile(file, getInt(BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_SIZE),
                getBoolean(BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_DIRECT));
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
            return new RelayFile(nextFile, getInt(BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_SIZE),
                getBoolean(BINLOG_X_TRANSMIT_WRITE_FILE_BUFFER_DIRECT));
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
