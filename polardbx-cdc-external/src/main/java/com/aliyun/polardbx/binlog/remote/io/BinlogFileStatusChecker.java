/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.io;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author chengjin, yudong
 * <p>
 * 检查binlog文件的状态
 */
public class BinlogFileStatusChecker {

    private static final Logger logger = LoggerFactory.getLogger(BinlogFileStatusChecker.class);

    private final IFileCursorProvider provider;
    private final String stream;

    public BinlogFileStatusChecker(IFileCursorProvider provider, String stream) {
        this.provider = provider;
        this.stream = stream;
    }

    public boolean needWait(int alreadyReadLen, String currentFile) {
        BinlogCursor cursor = provider.getCursor(stream);
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
        BinlogCursor cursor = provider.getCursor(stream);
        if (cursor == null) {
            return false;
        }
        return !fileName.equalsIgnoreCase(cursor.getFileName());
    }
}
