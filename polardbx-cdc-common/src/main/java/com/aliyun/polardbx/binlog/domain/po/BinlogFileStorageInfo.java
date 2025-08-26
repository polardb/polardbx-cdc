/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogFileStorageInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.inst_id")
    private String instId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.engine")
    private String engine;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.external_endpoint")
    private String externalEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_classic_endpoint")
    private String internalClassicEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_vpc_endpoint")
    private String internalVpcEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.file_uri")
    private String fileUri;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_id")
    private String accessKeyId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_secret")
    private String accessKeySecret;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.priority")
    private Long priority;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.region_id")
    private String regionId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.azone_id")
    private String azoneId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.cache_policy")
    private Long cachePolicy;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.delete_policy")
    private Long deletePolicy;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.status")
    private Long status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.endpoint_ordinal")
    private Long endpointOrdinal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.file_system_conf")
    private String fileSystemConf;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.915+08:00", comments="Source Table: binlog_file_storage_info")
    public BinlogFileStorageInfo(Long id, String instId, String engine, String externalEndpoint, String internalClassicEndpoint, String internalVpcEndpoint, String fileUri, String accessKeyId, String accessKeySecret, Long priority, String regionId, String azoneId, Long cachePolicy, Long deletePolicy, Long status, Date gmtCreated, Date gmtModified, Long endpointOrdinal, String fileSystemConf) {
        this.id = id;
        this.instId = instId;
        this.engine = engine;
        this.externalEndpoint = externalEndpoint;
        this.internalClassicEndpoint = internalClassicEndpoint;
        this.internalVpcEndpoint = internalVpcEndpoint;
        this.fileUri = fileUri;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.priority = priority;
        this.regionId = regionId;
        this.azoneId = azoneId;
        this.cachePolicy = cachePolicy;
        this.deletePolicy = deletePolicy;
        this.status = status;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.endpointOrdinal = endpointOrdinal;
        this.fileSystemConf = fileSystemConf;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source Table: binlog_file_storage_info")
    public BinlogFileStorageInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.engine")
    public String getEngine() {
        return engine;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.916+08:00", comments="Source field: binlog_file_storage_info.engine")
    public void setEngine(String engine) {
        this.engine = engine == null ? null : engine.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.external_endpoint")
    public String getExternalEndpoint() {
        return externalEndpoint;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.external_endpoint")
    public void setExternalEndpoint(String externalEndpoint) {
        this.externalEndpoint = externalEndpoint == null ? null : externalEndpoint.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_classic_endpoint")
    public String getInternalClassicEndpoint() {
        return internalClassicEndpoint;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_classic_endpoint")
    public void setInternalClassicEndpoint(String internalClassicEndpoint) {
        this.internalClassicEndpoint = internalClassicEndpoint == null ? null : internalClassicEndpoint.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_vpc_endpoint")
    public String getInternalVpcEndpoint() {
        return internalVpcEndpoint;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.internal_vpc_endpoint")
    public void setInternalVpcEndpoint(String internalVpcEndpoint) {
        this.internalVpcEndpoint = internalVpcEndpoint == null ? null : internalVpcEndpoint.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.file_uri")
    public String getFileUri() {
        return fileUri;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.file_uri")
    public void setFileUri(String fileUri) {
        this.fileUri = fileUri == null ? null : fileUri.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_id")
    public String getAccessKeyId() {
        return accessKeyId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_id")
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId == null ? null : accessKeyId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_secret")
    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.access_key_secret")
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret == null ? null : accessKeySecret.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.priority")
    public Long getPriority() {
        return priority;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.priority")
    public void setPriority(Long priority) {
        this.priority = priority;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.region_id")
    public String getRegionId() {
        return regionId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.region_id")
    public void setRegionId(String regionId) {
        this.regionId = regionId == null ? null : regionId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.917+08:00", comments="Source field: binlog_file_storage_info.azone_id")
    public String getAzoneId() {
        return azoneId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.azone_id")
    public void setAzoneId(String azoneId) {
        this.azoneId = azoneId == null ? null : azoneId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.cache_policy")
    public Long getCachePolicy() {
        return cachePolicy;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.cache_policy")
    public void setCachePolicy(Long cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.delete_policy")
    public Long getDeletePolicy() {
        return deletePolicy;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.delete_policy")
    public void setDeletePolicy(Long deletePolicy) {
        this.deletePolicy = deletePolicy;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.status")
    public Long getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.status")
    public void setStatus(Long status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.endpoint_ordinal")
    public Long getEndpointOrdinal() {
        return endpointOrdinal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.endpoint_ordinal")
    public void setEndpointOrdinal(Long endpointOrdinal) {
        this.endpointOrdinal = endpointOrdinal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.file_system_conf")
    public String getFileSystemConf() {
        return fileSystemConf;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.918+08:00", comments="Source field: binlog_file_storage_info.file_system_conf")
    public void setFileSystemConf(String fileSystemConf) {
        this.fileSystemConf = fileSystemConf == null ? null : fileSystemConf.trim();
    }
}