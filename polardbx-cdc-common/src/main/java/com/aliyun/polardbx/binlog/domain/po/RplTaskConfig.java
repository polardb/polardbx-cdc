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

public class RplTaskConfig {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.619+08:00", comments="Source field: rpl_task_config.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.memory")
    private Integer memory;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.extractor_config")
    private String extractorConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.pipeline_config")
    private String pipelineConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.622+08:00", comments="Source field: rpl_task_config.applier_config")
    private String applierConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.613+08:00", comments="Source Table: rpl_task_config")
    public RplTaskConfig(Long id, Date gmtCreated, Date gmtModified, Long taskId, Integer memory, String extractorConfig, String pipelineConfig, String applierConfig) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.taskId = taskId;
        this.memory = memory;
        this.extractorConfig = extractorConfig;
        this.pipelineConfig = pipelineConfig;
        this.applierConfig = applierConfig;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.618+08:00", comments="Source Table: rpl_task_config")
    public RplTaskConfig() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.62+08:00", comments="Source field: rpl_task_config.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.memory")
    public Integer getMemory() {
        return memory;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.memory")
    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.extractor_config")
    public String getExtractorConfig() {
        return extractorConfig;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.621+08:00", comments="Source field: rpl_task_config.extractor_config")
    public void setExtractorConfig(String extractorConfig) {
        this.extractorConfig = extractorConfig == null ? null : extractorConfig.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.622+08:00", comments="Source field: rpl_task_config.pipeline_config")
    public String getPipelineConfig() {
        return pipelineConfig;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.622+08:00", comments="Source field: rpl_task_config.pipeline_config")
    public void setPipelineConfig(String pipelineConfig) {
        this.pipelineConfig = pipelineConfig == null ? null : pipelineConfig.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.622+08:00", comments="Source field: rpl_task_config.applier_config")
    public String getApplierConfig() {
        return applierConfig;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.622+08:00", comments="Source field: rpl_task_config.applier_config")
    public void setApplierConfig(String applierConfig) {
        this.applierConfig = applierConfig == null ? null : applierConfig.trim();
    }
}