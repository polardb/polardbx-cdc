/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

import lombok.Data;

/**
 * @author chengjin.lyf on 2019/2/14 11:29 AM
 * @since 1.0.25
 */
@Data
public class DBInstanceAttribute {

    public static final String CATEGORY_BASIC = "Basic";

    /**
     * 实例ID。
     */
    private String DBInstanceId;
    /**
     * 实例付费方式：
     * Postpaid：按量付费；
     * Prepaid：包年包月。
     */
    private String PayType;
    /**
     * 实例类型：
     * Primary：主实例；
     * Readonly：只读实例；
     * Guard：灾备实例；
     * Temp：临时实例。
     */
    private String DBInstanceType;

    /**
     * 实例的系列。
     * Basic：单机基础版；
     * HighAvailability：双机高可用版；
     * Finance：金融版（三节点企业版）；
     * AlwaysOn：SQL Server 2017集群版。
     */
    private String Category;

    /**
     * 实例的网络类：
     * Classic：经典网络；
     * VPC：VPC网络。
     */
    private String InstanceNetworkType;

    /**
     * 实例的访问模式：
     * Performance：标准访问模式；
     * Safty：高安全访问模式
     */
    private String ConnectionMode;

    /**
     * 地域。
     */
    private String RegionId;

    /**
     * 可用区。
     */
    private String ZoneId;
    /**
     * 连接地址。
     */
    private String ConnectionString;

    /**
     * 端口号。
     */
    private String Port;

    /**
     * 数据库类型。
     */
    private String Engine;

    /**
     * 数据库版本。
     */
    private String EngineVersion;

    /**
     * 实例规格族：
     * s：共享型；
     * x：通用型；
     * d：独享套餐；
     * h：独占物理机。
     */
    private String DBInstanceClassType;

    /**
     * 实例内存，单位：M。
     */
    private Long DBInstanceMemory;

    /**
     * 实例存储空间，单位：GB。
     */
    private Integer DBInstanceStorage;

    /**
     * 网络连接方式：
     * Internet：外网；
     * Intranet：内网。
     */
    private String DBInstanceNetType;

    /**
     * 实例状态，详见实例状态表{@linkplain https://help.aliyun.com/document_detail/26315.html?spm=a2c4g.11186623.2.13.56e966e1fPTToq#reference-nyz-nnn-12b}。
     */
    private String DBInstanceStatus;

    /**
     * 实例备注。
     */
    private String DBInstanceDescription;

    /**
     * 实例锁定模式：
     * Unlock：正常；
     * ManualLock：手动触发锁定；
     * LockByExpiration：实例过期自动锁定；
     * LockByRestoration：实例回滚前的自动锁定；
     * LockByDiskQuota：实例空间满自动锁定。
     */
    private String LockMode;

    /**
     * 实例被锁定的原因。
     */
    private String LockReason;

    /**
     * VPC ID。
     */
    private String VpcId;

    /**
     * VSwitch ID。
     */
    private String VSwitchId;

    /**
     * 经典网络切换到专有网络时，会生成一个新的实例Id信息。
     */
    private String VpcCloudInstanceId;

    /**
     * 资源组ID。
     */
    private String ResourceGroupId;

    /**
     * 实例到期时间，按量付费实例无到期时间。
     */
    private String ExpireTime;

    /**
     * 最大IO请求次数，即IOPS。
     */
    private Integer MaxIOPS;

    /**
     * 最大实例并发连接数。
     */
    private Integer MaxConnections;

    /**
     * 实例CPU。
     */
    private String DBInstanceCPU;

    /**
     * 主实例的ID，如果没有返回此参数（即为null）则该实例是主实例。
     */
    private String MasterInstanceId;
}
