/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandler;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV1;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV2;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.SeekResult;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.channels.ClosedByInterruptException;

/**
 * @author zm
 * 仅实验室环境下会添加该监听类，用于监听binlog file的各个阶段的功能是否正常
 * 目前仅用于检查seekLastTso，校验第一个文件没有正常rotate时能否恢复
 */
@Slf4j
public class BinlogFileLabCheckListener implements IBinlogListener {
    @Getter
    private String lastErrMsg;

    /**
     * 当binlog文件创建时，调用此方法
     *
     * @param file binlog文件
     */
    @Override
    public void onCreateFile(File file) {
        // nothing to do
    }

    /**
     * 当binlog文件发生rotate时，调用此方法
     *
     * @param currentFile 当前binlog文件
     * @param nextFile 下一个binlog文件
     */
    @Override
    public void onRotateFile(File currentFile, String nextFile) {
        // nothing to do
    }

    /**
     * 当binlog文件结束时，调用此方法
     * 1. 强行打断binlog.000001的上传，然后尝试恢复它。
     * 2. 获取lastTso，并比较与binlogEndInfo是否一致，以此校验seekLastTso逻辑是否正确
     *
     * @param file binlog文件
     * @param binlogEndInfo binlog文件结束信息
     */
    @Override
    public void onFinishFile(File file, BinlogEndInfo binlogEndInfo) {
        if (BinlogFileUtil.getBinlogSequence(file.getName()) == 1) {
            boolean shouldCheck = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_FIRST_FILE_CHECK_ENABLED);
            if (shouldCheck) {
                DynamicApplicationConfig.setValue(ConfigKeys.BINLOG_FIRST_FILE_CHECK_ENABLED, "false");
                file.delete();
                Runtime.getRuntime().halt(0);
            }
        }
        if (binlogEndInfo != null) {
            checkSeekLastTso(file, binlogEndInfo.getLastEventTso(), binlogEndInfo.getLastXid(), true);
            checkSeekLastTso(file, binlogEndInfo.getLastEventTso(), binlogEndInfo.getLastXid(), false);
        }
    }

    private void checkSeekLastTso(File file, String tso, long xid, boolean optimize) {
        String version = optimize ? "v2" : "v1";
        try {
            BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 1, false, null, false);
            BinlogFileSeekHandler seekHandler;
            if (optimize) {
                seekHandler = new BinlogFileSeekHandlerV2();
            } else {
                seekHandler = new BinlogFileSeekHandlerV1();
            }
            SeekResult seekResult = seekHandler.seekLastTso(binlogFile, 0, 1024 * 1024, 4);
            if (!seekResult.getLastTso().equalsIgnoreCase(tso)) {
                lastErrMsg = file.getName() + ":Failed:" + tso + ":" + seekResult.getLastTso() + ":" + version;
                LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK, lastErrMsg);
            }
            Long seekXid = seekResult.getLastXid();
            if (seekXid != null && !(seekXid.equals(xid))) {
                lastErrMsg = file.getName() + ":Failed:" + xid + ":" + seekResult.getLastXid() + ":" + version;
                LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK, lastErrMsg);
            }
        } catch (Throwable e) {
            // 忽略由于线程中断导致的报错
            if (!(e instanceof InterruptedException) && !(e.getCause() instanceof ClosedByInterruptException)) {
                log.error("check file {} last tso failed, tso: {}", file.getName(), tso, e);
                lastErrMsg = file.getName() + ":Failed:" + tso + ":" + version;
                LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK, lastErrMsg);
            }
        }
        // 该文件已被校验完毕
        LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK, file.getName());
    }

    /**
     * 当binlog文件被删除时，调用此方法
     *
     * @param file binlog文件
     */
    @Override
    public void onDeleteFile(File file) {
        // nothing to do
    }

    @Override
    public void stop() {
        // nothing to do
    }
}
