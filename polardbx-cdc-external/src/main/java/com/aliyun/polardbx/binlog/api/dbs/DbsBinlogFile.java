/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class DbsBinlogFile {
    /**
     * 日志文件ID
     */
    private String ArchiveLogId;
    /**
     * 日志文件名
     */
    private String LogFileName;
    /**
     * 日志开始时间
     */
    private String LogBeginTime;
    /**
     * 日志结束时间
     */
    private String LogEndTime;
    /**
     * 日志文件大小,单位Byte
     */
    private Long LogFileSize;
    /**
     * 日志备份状态
     */
    private String LogStatus;
    /**
     * 备份实例所在编号
     */
    private Long HostInstanceId;
    /**
     * 日志外网下载地址
     */
    private String DownloadLink;

    /**
     * 日志外网下载地址
     */
    private String DownloadUrl;
    /**
     * 日志内网下载地址
     */
    private String IntranetDownloadLink;
    /**
     * 校验码
     */
    private String Checksum;
    /**
     * 实例名
     */
    private String InstanceName;
    /**
     * 日志存储位置
     */
    private String Location;
    /**
     * 日志文件锁定次数
     */
    private Integer Locks;
    /**
     * 存储池ID（DBStack场景）
     */
    private String StorageEntityId;
    /**
     * 日志文件总大小
     */
    private Long TotalLogSize;
}
