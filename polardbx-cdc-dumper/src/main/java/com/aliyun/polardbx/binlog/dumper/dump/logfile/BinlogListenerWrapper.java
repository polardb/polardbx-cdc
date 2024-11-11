/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 维护一个binlog文件监听者列表，当binlog文件发生变化时，调用列表中所有监听者的对应方法
 *
 * @author chengjin, yudong
 */
public class BinlogListenerWrapper implements IBinlogListener {
    private static final Logger logger = LoggerFactory.getLogger(BinlogListenerWrapper.class);
    private final List<IBinlogListener> binlogListeners = new CopyOnWriteArrayList<>();

    public void addListener(IBinlogListener listener) {
        binlogListeners.add(listener);
    }

    @Override
    public void onCreateFile(File file) {
        try {
            logger.info("file:{} is created", file.getName());
            binlogListeners.forEach(l -> l.onCreateFile(file));
        } catch (Exception e) {
            logger.error("on create error", e);
        }
    }

    @Override
    public void onRotateFile(File currentFile, String nextFile) {
        try {
            logger.info("file:{} is rotated to {}", currentFile.getName(), nextFile);
            binlogListeners.forEach(l -> l.onRotateFile(currentFile, nextFile));
        } catch (Exception e) {
            logger.error("on rotate error", e);
        }
    }

    @Override
    public void onFinishFile(File file, BinlogEndInfo binlogEndInfo) {
        try {
            logger.info("file is finished, file name:{}, logEndInfo:{}", file.getName(), binlogEndInfo);
            binlogListeners.forEach(l -> l.onFinishFile(file, binlogEndInfo));
        } catch (Exception e) {
            logger.error("on finish error", e);
        }
    }

    @Override
    public void onDeleteFile(File file) {
        try {
            logger.info("file:{} is deleted", file.getName());
            binlogListeners.forEach(l -> l.onDeleteFile(file));
        } catch (Exception e) {
            logger.error("on delete error", e);
        }
    }
}
