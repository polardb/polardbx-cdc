/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.filter;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;

/**
 * 一个尽可能少decode的binlog event表级DML过滤器
 * 暂时不会过滤压缩事件
 *
 * @author zm
 */
public class LogBufferFilter {
    /**
     * 需要被过滤或者允许的tableName合集
     */
    @Getter
    protected volatile Set<String> tableNameSet;
    /**
     * 已经从表名转换为tableId的tableId合集
     */
    private final Set<Long> tableIdSet;
    /**
     * 需要被过滤或者允许的tableId合集
     */
    private final Set<Long> tableIdIgnoreOrAllowSet;
    @Getter
    private volatile boolean whiteListMode;
    @Setter
    private FormatDescriptionLogEvent formatDescriptionLogEvent;
    /**
     * 监控指标
     */
    @Setter
    private Consumer<Long> addBinlogEventThroughput = null;
    /**
     * 监控指标
     */
    @Setter
    private Consumer<Long> addBinlogEventSize = null;
    /**
     * 控制是否开启过滤
     */
    @Setter
    @Getter
    private volatile boolean enabled = true;

    public LogBufferFilter(Set<String> tableNameSet, boolean whiteListMode) {
        this.tableNameSet = tableNameSet;
        this.tableIdSet = new HashSet<>();
        this.tableIdIgnoreOrAllowSet = new HashSet<>();
        this.whiteListMode = whiteListMode;
    }

    /**
     * 遇到要filter掉的event时，将把pos向后移动
     * (logBuffer内一定有一个完整的event，如果没有，decode出来是null，也会重新fetch()然后触发自动扩容，然后继续filter，此时就是完整的了)
     */
    public boolean filter(LogBuffer logBuffer) {
        if (!enabled) {
            return false;
        }
        if (tableNameSet == null || tableNameSet.isEmpty()) {
            return false;
        }
        if (logBuffer.remaining() < 19) {
            return false;
        }
        // 理论上 offset 恒定为0
        int offset = logBuffer.position();
        // skip timestamp
        logBuffer.forward(4);
        int eventType = logBuffer.getUint8();
        // skip serverId
        logBuffer.forward(4);
        int eventLength = (int) logBuffer.getUint32();
        if (logBuffer.limit() < eventLength) {
            // 不完整的事件
            logBuffer.position(offset);
            return false;
        }
        // skip remaining 6 header bytes
        logBuffer.forward(6);
        /*
          这个锁的目的是为了控制两个集合的原子性
          列存会在链路运行期间动态调整AcceptTable，每次调整时需要重新填充tableIdSet和tableIdIgnoreOrAllowSet两个集合
          为避免和主sync链路中的tableIdSet和tableIdIgnoreOrAllowSet的操作冲突，加个锁
         */
        synchronized (tableIdSet) {
            if (eventType == LogEvent.TABLE_MAP_EVENT) {
                if (!tableNameSet.isEmpty()) {
                    long tableId = getTableId(logBuffer);
                    assert tableId != -1;
                    if (!tableIdSet.contains(tableId)) {
                        // 一个TableId仅需处理一次，避免多次读取字符串
                        tableIdSet.add(tableId);
                        // flags
                        logBuffer.forward(2);
                        String dbName = logBuffer.getString();
                        logBuffer.forward(1);
                        String tbName = logBuffer.getString();
                        if (tableNameSet.contains(dbName + "." + tbName)) {
                            tableIdIgnoreOrAllowSet.add(tableId);
                        }
                    }
                    return trySkipEvent(logBuffer, offset, eventLength, tableId);
                }
            } else if (isDmlEvent(eventType)) {
                if (tableIdSet.isEmpty()) {
                    // 重新设置过滤表后，还没有处理过一个TableMapEvent, 这个event(也只有这一个)无法判断是否要过滤。
                    logBuffer.position(offset);
                    return false;
                }
                if (!tableIdIgnoreOrAllowSet.isEmpty() || whiteListMode) {
                    long tableId = getTableId(logBuffer);
                    return trySkipEvent(logBuffer, offset, eventLength, tableId);
                }
            } else if (eventType == LogEvent.ROTATE_EVENT) {
                tableIdSet.clear();
                tableIdIgnoreOrAllowSet.clear();
                logBuffer.position(offset);
            }
            logBuffer.position(offset);
            return false;
        }
    }

    /**
     * 尝试跳过当前事件。
     * 根据白名单/黑名单模式和tableId是否在过滤集合中，决定跳过或保留。
     * 跳过时会移动logBuffer的位置到事件末尾，并记录监控指标。
     */
    private boolean trySkipEvent(LogBuffer logBuffer, int offset, int eventLength, long tableId) {
        boolean contains = tableIdIgnoreOrAllowSet.contains(tableId);
        if (whiteListMode && !contains) {
            skipEvent(logBuffer, offset + eventLength, eventLength);
            return true;
        } else if (!whiteListMode && contains) {
            skipEvent(logBuffer, offset + eventLength, eventLength);
            return true;
        } else {
            // 重置位点
            logBuffer.position(offset);
            return false;
        }
    }

    /**
     * 判断事件类型是否为DML事件（INSERT/UPDATE/DELETE，包括V1和V2版本）
     */
    public boolean isDmlEvent(int eventType) {
        return eventType == LogEvent.WRITE_ROWS_EVENT || eventType == LogEvent.UPDATE_ROWS_EVENT
            || eventType == LogEvent.DELETE_ROWS_EVENT || eventType == LogEvent.WRITE_ROWS_EVENT_V1
            || eventType == LogEvent.UPDATE_ROWS_EVENT_V1 || eventType == LogEvent.DELETE_ROWS_EVENT_V1;
    }

    /**
     * 根据FormatDescriptionEvent获取tableId的字节长度。
     * MySQL 5.x之前为4字节，之后为6字节。
     */
    public int getTableIdLength() {
        if (formatDescriptionLogEvent.getPostHeaderLen()[TABLE_MAP_EVENT - 1] == 6) {
            return 4;
        } else {
            return 6;
        }
    }

    /**
     * 从LogBuffer中读取tableId，长度取决于FormatDescriptionEvent的配置
     */
    public long getTableId(LogBuffer logBuffer) {
        int tableIdLength = getTableIdLength();
        if (tableIdLength == 4) {
            return logBuffer.getUint32();
        } else if (tableIdLength == 6) {
            return logBuffer.getUlong48();
        }
        return -1;
    }

    /**
     * 跳过一个事件，将logBuffer位置移动到事件末尾，并消费已读取的字节
     */
    public void skipEvent(LogBuffer logBuffer, int endPos, int eventLen) {
        logBuffer.position(endPos);
        logBuffer.consume(eventLen);
        if (null != addBinlogEventThroughput) {
            addBinlogEventThroughput.accept(1L);
        }
        if (null != addBinlogEventSize) {
            addBinlogEventSize.accept((long) eventLen);
        }
    }

    /**
     * 设置白名单表集合，同时清空tableId缓存。
     * 加锁保证tableIdSet和tableIdIgnoreOrAllowSet的原子性更新。
     */
    public void setAcceptTable(Set<String> acceptTableSet) {
        synchronized (tableIdSet) {
            this.tableNameSet = acceptTableSet;
            this.whiteListMode = true;
            tableIdSet.clear();
            tableIdIgnoreOrAllowSet.clear();
        }
    }

    /**
     * 设置黑名单表集合，同时清空tableId缓存。
     */
    public void setIgnoreTable(Set<String> ignoreTable) {
        synchronized (tableIdSet) {
            this.tableNameSet = ignoreTable;
            this.whiteListMode = false;
            tableIdSet.clear();
            tableIdIgnoreOrAllowSet.clear();
        }
    }

    /**
     *
     */
    @Override
    public String toString() {
        return "LogBufferFilter{" +
            "tableNameSet=" + tableNameSet +
            ", tableIdSet=" + tableIdSet +
            ", tableIdIgnoreOrAllowSet=" + tableIdIgnoreOrAllowSet +
            ", whiteListMode=" + whiteListMode +
            ", enabled=" + enabled +
            '}';
    }
}
