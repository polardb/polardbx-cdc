package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
import org.mybatis.dynamic.sql.insert.render.MultiRowInsertStatementProvider;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSL;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateModel;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

import javax.annotation.Generated;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.semiSnapshotInfo;
import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.storageInstId;
import static com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface SemiSnapshotInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.135+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, storageInstId);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.125+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.126+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.126+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<SemiSnapshotInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.127+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<SemiSnapshotInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.127+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<SemiSnapshotInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.129+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<SemiSnapshotInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.129+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.13+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.13+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.131+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.131+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int insert(SemiSnapshotInfo record) {
        return MyBatis3Utils.insert(this::insert, record, semiSnapshotInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(storageInstId).toProperty("storageInstId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.133+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int insertMultiple(Collection<SemiSnapshotInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, semiSnapshotInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(storageInstId).toProperty("storageInstId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.134+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int insertSelective(SemiSnapshotInfo record) {
        return MyBatis3Utils.insert(this::insert, record, semiSnapshotInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(tso).toPropertyWhenPresent("tso", record::getTso)
                .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.136+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default Optional<SemiSnapshotInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.137+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default List<SemiSnapshotInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.138+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default List<SemiSnapshotInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.138+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default Optional<SemiSnapshotInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.139+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, semiSnapshotInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.139+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    static UpdateDSL<UpdateModel> updateAllColumns(SemiSnapshotInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(storageInstId).equalTo(record::getStorageInstId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.14+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(SemiSnapshotInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.141+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int updateByPrimaryKey(SemiSnapshotInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-02-14T10:39:59.141+08:00",
        comments = "Source Table: binlog_semi_snapshot")
    default int updateByPrimaryKeySelective(SemiSnapshotInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .where(id, isEqualTo(record::getId))
        );
    }
}