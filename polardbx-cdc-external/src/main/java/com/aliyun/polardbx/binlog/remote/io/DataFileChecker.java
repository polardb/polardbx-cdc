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
package com.aliyun.polardbx.binlog.remote.io;

import com.aliyun.polardbx.binlog.domain.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataFileChecker {

    private static final Logger logger = LoggerFactory.getLogger(DataFileChecker.class);

    private final IFileCursorProvider provider;
    private final String stream;

    public DataFileChecker(IFileCursorProvider provider, String stream) {
        this.provider = provider;
        this.stream = stream;
    }
    
    public boolean needWait(int alreadyReadLen, String currentFile) {
        Cursor cursor = provider.getCursor(stream);
        if (logger.isDebugEnabled()) {
            logger.debug("check need wait : " + cursor);
        }

        if (cursor == null) {
            return true;
        }

        // 新文件已经创建，但cursor可能还未更新，会出现currentFile大于lastCursor的情况，需要等待
        if (currentFile.compareTo(cursor.getFileName()) > 0) {
            return true;
        } else {
            return (alreadyReadLen >= cursor.getFilePosition() && cursor.getFileName()
                .equalsIgnoreCase(currentFile));
        }
    }

    /**
     * 判断该文件是否已经写完，如果当前流的最新Cursor的文件名不等于fileName，
     * 说明当前没有在写fileName这个文件，我们就认为已经成功rotate到fileName之后的文件
     */
    public boolean isCompleteFile(String fileName) {
        Cursor cursor = provider.getCursor(stream);
        if (cursor == null) {
            return false;
        }
        return !fileName.equalsIgnoreCase(cursor.getFileName());
    }
}
