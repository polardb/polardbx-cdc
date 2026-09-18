/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;

/**
 * CDC事件数据的封装类，承载一个binlog事件在解析管道中的全部信息。
 * 在事件从解码 -> 预处理 -> 行事件解析 -> 输出的各阶段中流转。
 */
public class CdcEventData {
    /**
     * binlog文件名，如 "binlog.000004"
     */
    private String binlogFileName;
    /**
     * 当前事件在binlog文件中的位置偏移量
     */
    private long position;
    /**
     * 解析后的DBMS事件（DDL/DML/心跳等），预处理阶段生成，行事件在并行解析阶段填充
     */
    private DBMSEvent event;
    /**
     * 最后一次获取的trace信息，用于关联行事件与其事务的追踪信息
     */
    private String lastTraceInfo;
    /**
     * 原始LogEvent，在行事件解析完成后会被置为null以释放内存
     */
    private LogEvent logEvent;

    public CdcEventData(String binlogFileName, long position, DBMSEvent event) {
        this.binlogFileName = binlogFileName;
        this.position = position;
        this.event = event;
    }

    public CdcEventData() {
    }

    public void setBinlogFileName(String binlogFileName) {
        this.binlogFileName = binlogFileName;
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getPosition() {
        return position;
    }

    public void setEvent(DBMSEvent event) {
        this.event = event;
    }

    public DBMSEvent getEvent() {
        return event;
    }

    public void setLastTraceInfo(String lastTraceInfo) {
        this.lastTraceInfo = lastTraceInfo;
    }

    public String getLastTraceInfo() {
        return lastTraceInfo;
    }

    public void setLogEvent(LogEvent logEvent) {
        this.logEvent = logEvent;
    }

    public LogEvent getLogEvent() {
        return logEvent;
    }

    /**
     * 浅拷贝当前对象，复制所有字段引用（非深拷贝）
     */
    public CdcEventData copy() {
        CdcEventData cdcEventData = new CdcEventData();
        cdcEventData.setBinlogFileName(binlogFileName);
        cdcEventData.setPosition(position);
        cdcEventData.setEvent(event);
        cdcEventData.setLastTraceInfo(lastTraceInfo);
        cdcEventData.setLogEvent(logEvent);
        return cdcEventData;
    }
}
