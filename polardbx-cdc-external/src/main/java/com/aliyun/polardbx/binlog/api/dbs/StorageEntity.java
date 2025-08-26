/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class StorageEntity {
    private String type;
    private String OssUid;
    private String OssEndpoint;
    private String OssBucket;
    private String OssRegion;
    private String UserAKID;
    private String UserAKSecret;
    //type = nas
    private String NasUid;
    private String OriginalIp;
    private String OriginalPort;
    private String Mountpoint;
    private String VpcId;
    private String Protocol;
    private String ProtocolVersion;
    private String account;
    private String password;
    //type = lindorm
    private String accessKey;
    private String accessSecret;
    private String region;
    private String endpoint;
    private String bucket;
}
