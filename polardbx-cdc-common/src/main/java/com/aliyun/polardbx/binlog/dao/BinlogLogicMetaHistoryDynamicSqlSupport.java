package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogLogicMetaHistoryDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.184+08:00", comments="Source Table: binlog_logic_meta_history")
    public static final BinlogLogicMetaHistory binlogLogicMetaHistory = new BinlogLogicMetaHistory();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source field: binlog_logic_meta_history.id")
    public static final SqlColumn<Integer> id = binlogLogicMetaHistory.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source field: binlog_logic_meta_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogLogicMetaHistory.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source field: binlog_logic_meta_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogLogicMetaHistory.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source field: binlog_logic_meta_history.tso")
    public static final SqlColumn<String> tso = binlogLogicMetaHistory.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source field: binlog_logic_meta_history.db_name")
    public static final SqlColumn<String> dbName = binlogLogicMetaHistory.dbName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.table_name")
    public static final SqlColumn<String> tableName = binlogLogicMetaHistory.tableName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.sql_kind")
    public static final SqlColumn<String> sqlKind = binlogLogicMetaHistory.sqlKind;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.type")
    public static final SqlColumn<Byte> type = binlogLogicMetaHistory.type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.ddl_record_id")
    public static final SqlColumn<Long> ddlRecordId = binlogLogicMetaHistory.ddlRecordId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.ddl_job_id")
    public static final SqlColumn<Long> ddlJobId = binlogLogicMetaHistory.ddlJobId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.ddl")
    public static final SqlColumn<String> ddl = binlogLogicMetaHistory.ddl;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.topology")
    public static final SqlColumn<String> topology = binlogLogicMetaHistory.topology;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.186+08:00", comments="Source field: binlog_logic_meta_history.ext_info")
    public static final SqlColumn<String> extInfo = binlogLogicMetaHistory.extInfo;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-10T17:59:36.185+08:00", comments="Source Table: binlog_logic_meta_history")
    public static final class BinlogLogicMetaHistory extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> sqlKind = column("sql_kind", JDBCType.VARCHAR);

        public final SqlColumn<Byte> type = column("type", JDBCType.TINYINT);

        public final SqlColumn<Long> ddlRecordId = column("ddl_record_id", JDBCType.BIGINT);

        public final SqlColumn<Long> ddlJobId = column("ddl_job_id", JDBCType.BIGINT);

        public final SqlColumn<String> ddl = column("ddl", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> topology = column("topology", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extInfo = column("ext_info", JDBCType.LONGVARCHAR);

        public BinlogLogicMetaHistory() {
            super("binlog_logic_meta_history");
        }
    }
}