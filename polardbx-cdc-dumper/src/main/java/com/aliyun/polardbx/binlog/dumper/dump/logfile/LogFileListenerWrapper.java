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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogFileListenerWrapper implements LogFileListener {

    private static final Logger logger = LoggerFactory.getLogger(LogFileListenerWrapper.class);

    private List<LogFileListener> logFileListeners = new CopyOnWriteArrayList<>();

    public void addLogFileListener(LogFileListener l) {
        logFileListeners.add(l);
    }

    @Override
    public void onCreateFile(File file) {
        try {
            for (LogFileListener logFileListener : logFileListeners) {
                logFileListener.onCreateFile(file);
            }
        } catch (Exception e) {
            logger.error("on create error", e);
        }

    }

    @Override
    public void onRotateFile(File currentFile, String nextFile) {
        try {
            for (LogFileListener logFileListener : logFileListeners) {
                logFileListener.onRotateFile(currentFile, nextFile);
            }
        } catch (Exception e) {
            logger.error("on rotate error", e);
        }

    }

    @Override
    public void onFinishFile(File file, LogEndInfo logEndInfo) {
        try {
            for (LogFileListener logFileListener : logFileListeners) {
                logFileListener.onFinishFile(file, logEndInfo);
            }
        } catch (Exception e) {
            logger.error("on finish error", e);
        }

    }

    @Override
    public void onDeleteFile(File file) {
        try {
            for (LogFileListener logFileListener : logFileListeners) {
                logFileListener.onDeleteFile(file);
            }
        } catch (Exception e) {
            logger.error("on delete error", e);
        }

    }

}
