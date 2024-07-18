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

import java.util.Date;
import javax.annotation.Generated;

public class RplSyncPoint {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.056842+08:00", comments="Source field: rpl_sync_point.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057208+08:00", comments="Source field: rpl_sync_point.primary_tso")
    private String primaryTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057304+08:00", comments="Source field: rpl_sync_point.secondary_tso")
    private String secondaryTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057371+08:00", comments="Source field: rpl_sync_point.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.055849+08:00", comments="Source Table: rpl_sync_point")
    public RplSyncPoint(Long id, String primaryTso, String secondaryTso, Date createTime) {
        this.id = id;
        this.primaryTso = primaryTso;
        this.secondaryTso = secondaryTso;
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.056584+08:00", comments="Source Table: rpl_sync_point")
    public RplSyncPoint() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057146+08:00", comments="Source field: rpl_sync_point.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057184+08:00", comments="Source field: rpl_sync_point.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057235+08:00", comments="Source field: rpl_sync_point.primary_tso")
    public String getPrimaryTso() {
        return primaryTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057274+08:00", comments="Source field: rpl_sync_point.primary_tso")
    public void setPrimaryTso(String primaryTso) {
        this.primaryTso = primaryTso == null ? null : primaryTso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057327+08:00", comments="Source field: rpl_sync_point.secondary_tso")
    public String getSecondaryTso() {
        return secondaryTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.05735+08:00", comments="Source field: rpl_sync_point.secondary_tso")
    public void setSecondaryTso(String secondaryTso) {
        this.secondaryTso = secondaryTso == null ? null : secondaryTso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057398+08:00", comments="Source field: rpl_sync_point.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.057422+08:00", comments="Source field: rpl_sync_point.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}