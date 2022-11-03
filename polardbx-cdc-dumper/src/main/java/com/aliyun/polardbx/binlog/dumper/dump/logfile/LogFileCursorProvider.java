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

import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.io.IFileCursorProvider;

public class LogFileCursorProvider implements IFileCursorProvider {

    private final LogFileManager logFileManager;

    public LogFileCursorProvider(LogFileManager logFileManager) {
        this.logFileManager = logFileManager;
    }

    @Override
    public boolean needWait(int alreadyReadLen, String currentFile) {
        Cursor latestCursor = logFileManager.getLatestFileCursor();
        if (latestCursor == null) {
            return true;
        }

        // 新文件已经创建，但cursor可能还未更新，会出现currentFile大于lastCursor的情况，需要等待
        if (currentFile.compareTo(latestCursor.getFileName()) > 0) {
            return true;
        } else {
            return (alreadyReadLen >= latestCursor.getFilePosition() && latestCursor.getFileName()
                .equalsIgnoreCase(currentFile));
        }
    }

    @Override
    public boolean isCompleteFile(String currentFile) {
        Cursor latestCursor = logFileManager.getLatestFileCursor();
        if (latestCursor == null) {
            return false;
        }
        return !currentFile.equalsIgnoreCase(latestCursor.getFileName());
    }

}
