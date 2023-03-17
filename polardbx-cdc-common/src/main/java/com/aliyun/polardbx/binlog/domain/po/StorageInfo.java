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
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class StorageInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.inst_id")
    private String instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_inst_id")
    private String storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_master_inst_id")
    private String storageMasterInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.ip")
    private String ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.port")
    private Integer port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.xport")
    private Integer xport;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.user")
    private String user;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.storage_type")
    private Integer storageType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.inst_kind")
    private Integer instKind;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.region_id")
    private String regionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.azone_id")
    private String azoneId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.idc_id")
    private String idcId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.max_conn")
    private Integer maxConn;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.cpu_core")
    private Integer cpuCore;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.mem_size")
    private Integer memSize;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.is_vip")
    private Integer isVip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.passwd_enc")
    private String passwdEnc;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.extras")
    private String extras;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.411+08:00",
        comments = "Source Table: storage_info")
    public StorageInfo(Long id, Date gmtCreated, Date gmtModified, String instId, String storageInstId,
                       String storageMasterInstId, String ip, Integer port, Integer xport, String user,
                       Integer storageType, Integer instKind, Integer status, String regionId, String azoneId,
                       String idcId, Integer maxConn, Integer cpuCore, Integer memSize, Integer isVip, String passwdEnc,
                       String extras) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.instId = instId;
        this.storageInstId = storageInstId;
        this.storageMasterInstId = storageMasterInstId;
        this.ip = ip;
        this.port = port;
        this.xport = xport;
        this.user = user;
        this.storageType = storageType;
        this.instKind = instKind;
        this.status = status;
        this.regionId = regionId;
        this.azoneId = azoneId;
        this.idcId = idcId;
        this.maxConn = maxConn;
        this.cpuCore = cpuCore;
        this.memSize = memSize;
        this.isVip = isVip;
        this.passwdEnc = passwdEnc;
        this.extras = extras;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source Table: storage_info")
    public StorageInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.412+08:00",
        comments = "Source field: storage_info.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId == null ? null : storageInstId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_master_inst_id")
    public String getStorageMasterInstId() {
        return storageMasterInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.storage_master_inst_id")
    public void setStorageMasterInstId(String storageMasterInstId) {
        this.storageMasterInstId = storageMasterInstId == null ? null : storageMasterInstId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.xport")
    public Integer getXport() {
        return xport;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.xport")
    public void setXport(Integer xport) {
        this.xport = xport;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.413+08:00",
        comments = "Source field: storage_info.user")
    public String getUser() {
        return user;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.user")
    public void setUser(String user) {
        this.user = user == null ? null : user.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.storage_type")
    public Integer getStorageType() {
        return storageType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.storage_type")
    public void setStorageType(Integer storageType) {
        this.storageType = storageType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.inst_kind")
    public Integer getInstKind() {
        return instKind;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.inst_kind")
    public void setInstKind(Integer instKind) {
        this.instKind = instKind;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.region_id")
    public String getRegionId() {
        return regionId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.region_id")
    public void setRegionId(String regionId) {
        this.regionId = regionId == null ? null : regionId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.azone_id")
    public String getAzoneId() {
        return azoneId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.414+08:00",
        comments = "Source field: storage_info.azone_id")
    public void setAzoneId(String azoneId) {
        this.azoneId = azoneId == null ? null : azoneId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.idc_id")
    public String getIdcId() {
        return idcId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.idc_id")
    public void setIdcId(String idcId) {
        this.idcId = idcId == null ? null : idcId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.max_conn")
    public Integer getMaxConn() {
        return maxConn;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.max_conn")
    public void setMaxConn(Integer maxConn) {
        this.maxConn = maxConn;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.cpu_core")
    public Integer getCpuCore() {
        return cpuCore;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.cpu_core")
    public void setCpuCore(Integer cpuCore) {
        this.cpuCore = cpuCore;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.mem_size")
    public Integer getMemSize() {
        return memSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.mem_size")
    public void setMemSize(Integer memSize) {
        this.memSize = memSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.is_vip")
    public Integer getIsVip() {
        return isVip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.is_vip")
    public void setIsVip(Integer isVip) {
        this.isVip = isVip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.415+08:00",
        comments = "Source field: storage_info.passwd_enc")
    public String getPasswdEnc() {
        return passwdEnc;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.passwd_enc")
    public void setPasswdEnc(String passwdEnc) {
        this.passwdEnc = passwdEnc == null ? null : passwdEnc.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.extras")
    public String getExtras() {
        return extras;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.extras")
    public void setExtras(String extras) {
        this.extras = extras == null ? null : extras.trim();
    }
}