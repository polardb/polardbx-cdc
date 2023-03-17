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
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplTaskConfigDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSL;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateModel;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

@Mapper
public interface RplTaskConfigMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.647+08:00", comments="Source Table: rpl_task_config")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, taskId, memory, extractorConfig, pipelineConfig, applierConfig);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.632+08:00", comments="Source Table: rpl_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.633+08:00", comments="Source Table: rpl_task_config")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.633+08:00", comments="Source Table: rpl_task_config")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<RplTaskConfig> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.638+08:00", comments="Source Table: rpl_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="memory", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="extractor_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="pipeline_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="applier_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<RplTaskConfig> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.64+08:00", comments="Source Table: rpl_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="memory", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="extractor_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="pipeline_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="applier_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<RplTaskConfig> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.641+08:00", comments="Source Table: rpl_task_config")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.641+08:00", comments="Source Table: rpl_task_config")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.641+08:00", comments="Source Table: rpl_task_config")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.642+08:00", comments="Source Table: rpl_task_config")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.642+08:00", comments="Source Table: rpl_task_config")
    default int insert(RplTaskConfig record) {
        return MyBatis3Utils.insert(this::insert, record, rplTaskConfig, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(taskId).toProperty("taskId")
            .map(memory).toProperty("memory")
            .map(extractorConfig).toProperty("extractorConfig")
            .map(pipelineConfig).toProperty("pipelineConfig")
            .map(applierConfig).toProperty("applierConfig")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.644+08:00", comments="Source Table: rpl_task_config")
    default int insertSelective(RplTaskConfig record) {
        return MyBatis3Utils.insert(this::insert, record, rplTaskConfig, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(memory).toPropertyWhenPresent("memory", record::getMemory)
            .map(extractorConfig).toPropertyWhenPresent("extractorConfig", record::getExtractorConfig)
            .map(pipelineConfig).toPropertyWhenPresent("pipelineConfig", record::getPipelineConfig)
            .map(applierConfig).toPropertyWhenPresent("applierConfig", record::getApplierConfig)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.648+08:00", comments="Source Table: rpl_task_config")
    default Optional<RplTaskConfig> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.649+08:00", comments="Source Table: rpl_task_config")
    default List<RplTaskConfig> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.649+08:00", comments="Source Table: rpl_task_config")
    default List<RplTaskConfig> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.649+08:00", comments="Source Table: rpl_task_config")
    default Optional<RplTaskConfig> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.65+08:00", comments="Source Table: rpl_task_config")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.65+08:00", comments="Source Table: rpl_task_config")
    static UpdateDSL<UpdateModel> updateAllColumns(RplTaskConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(taskId).equalTo(record::getTaskId)
                .set(memory).equalTo(record::getMemory)
                .set(extractorConfig).equalTo(record::getExtractorConfig)
                .set(pipelineConfig).equalTo(record::getPipelineConfig)
                .set(applierConfig).equalTo(record::getApplierConfig);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.651+08:00", comments="Source Table: rpl_task_config")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplTaskConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(memory).equalToWhenPresent(record::getMemory)
                .set(extractorConfig).equalToWhenPresent(record::getExtractorConfig)
                .set(pipelineConfig).equalToWhenPresent(record::getPipelineConfig)
                .set(applierConfig).equalToWhenPresent(record::getApplierConfig);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.652+08:00", comments="Source Table: rpl_task_config")
    default int updateByPrimaryKey(RplTaskConfig record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(taskId).equalTo(record::getTaskId)
            .set(memory).equalTo(record::getMemory)
            .set(extractorConfig).equalTo(record::getExtractorConfig)
            .set(pipelineConfig).equalTo(record::getPipelineConfig)
            .set(applierConfig).equalTo(record::getApplierConfig)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.653+08:00", comments="Source Table: rpl_task_config")
    default int updateByPrimaryKeySelective(RplTaskConfig record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(memory).equalToWhenPresent(record::getMemory)
            .set(extractorConfig).equalToWhenPresent(record::getExtractorConfig)
            .set(pipelineConfig).equalToWhenPresent(record::getPipelineConfig)
            .set(applierConfig).equalToWhenPresent(record::getApplierConfig)
            .where(id, isEqualTo(record::getId))
        );
    }
}