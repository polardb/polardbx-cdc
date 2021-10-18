/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class ServerInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.316+08:00",
        comments = "Source field: server_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.inst_id")
    private String instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.inst_type")
    private Integer instType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.ip")
    private String ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.port")
    private Integer port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.htap_port")
    private Integer htapPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.mgr_port")
    private Integer mgrPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.mpp_port")
    private Integer mppPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.region_id")
    private String regionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.azone_id")
    private String azoneId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.idc_id")
    private String idcId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.cpu_core")
    private Integer cpuCore;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.mem_size")
    private Integer memSize;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.extras")
    private String extras;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.313+08:00",
        comments = "Source Table: server_info")
    public ServerInfo(Long id, Date gmtCreated, Date gmtModified, String instId, Integer instType, String ip,
                      Integer port, Integer htapPort, Integer mgrPort, Integer mppPort, Integer status, String regionId,
                      String azoneId, String idcId, Integer cpuCore, Integer memSize, String extras) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.instId = instId;
        this.instType = instType;
        this.ip = ip;
        this.port = port;
        this.htapPort = htapPort;
        this.mgrPort = mgrPort;
        this.mppPort = mppPort;
        this.status = status;
        this.regionId = regionId;
        this.azoneId = azoneId;
        this.idcId = idcId;
        this.cpuCore = cpuCore;
        this.memSize = memSize;
        this.extras = extras;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.316+08:00",
        comments = "Source Table: server_info")
    public ServerInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.317+08:00",
        comments = "Source field: server_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.inst_type")
    public Integer getInstType() {
        return instType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.inst_type")
    public void setInstType(Integer instType) {
        this.instType = instType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.htap_port")
    public Integer getHtapPort() {
        return htapPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.htap_port")
    public void setHtapPort(Integer htapPort) {
        this.htapPort = htapPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.mgr_port")
    public Integer getMgrPort() {
        return mgrPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.318+08:00",
        comments = "Source field: server_info.mgr_port")
    public void setMgrPort(Integer mgrPort) {
        this.mgrPort = mgrPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.mpp_port")
    public Integer getMppPort() {
        return mppPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.mpp_port")
    public void setMppPort(Integer mppPort) {
        this.mppPort = mppPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.region_id")
    public String getRegionId() {
        return regionId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.region_id")
    public void setRegionId(String regionId) {
        this.regionId = regionId == null ? null : regionId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.azone_id")
    public String getAzoneId() {
        return azoneId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.azone_id")
    public void setAzoneId(String azoneId) {
        this.azoneId = azoneId == null ? null : azoneId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.idc_id")
    public String getIdcId() {
        return idcId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.319+08:00",
        comments = "Source field: server_info.idc_id")
    public void setIdcId(String idcId) {
        this.idcId = idcId == null ? null : idcId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.cpu_core")
    public Integer getCpuCore() {
        return cpuCore;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.cpu_core")
    public void setCpuCore(Integer cpuCore) {
        this.cpuCore = cpuCore;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.mem_size")
    public Integer getMemSize() {
        return memSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.mem_size")
    public void setMemSize(Integer memSize) {
        this.memSize = memSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.extras")
    public String getExtras() {
        return extras;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.32+08:00",
        comments = "Source field: server_info.extras")
    public void setExtras(String extras) {
        this.extras = extras == null ? null : extras.trim();
    }
}