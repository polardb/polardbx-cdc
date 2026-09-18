/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.BitSet;

@Slf4j
public class RowsEventReformatTest extends RplBaseTestCase {
    // 由于该表指定了分表进行插入，所以插入的数据很有可能无法被查询出来（分区路由不一致）
    // 因此，将该表放入DataConsistencyTest校验黑名单，并进行手动dump校验
    private static final String DB_NAME = "zm_test_db";
    private static final String TABLE_NAME = "null_json_tb";
    private static final String CREATE_DATABASE_SQL = "create database if not exists %s mode = 'auto'";
    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE if not exists `%s`.`%s` ( \n"
            + "  `id` int NOT NULL AUTO_INCREMENT,\n"
            + "  `txt` json not null,\n"
            + "  `value` int,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") PARTITION BY HASH(`id`) PARTITIONS 4";
    private static final String INSERT_SQL = "/*+TDDL:node(0)*/insert into `%s` (`value`) values ('%s')";
    private static final String SET_SQL_MODE = "set sql_mode = ''";
    private static final String SHOW_MASTER_STATUS_SQL = "show master status";
    private static final String SHOW_TABLES_SQL = "/*+TDDL:node(0)*/show tables like '%s'";
    private static final String ALTER_TABLE_SQL = "/*+TDDL:node(0)*/alter table `%s` add column `v3` int";
    private static final String USE_DATABASE = "use `%s`";

    @Test
    @SneakyThrows
    public void testNullJson() {
        String file = "";
        long pos = 0;
        String phyTableName = "";

        try (Connection c = getPolardbxConnection()) {
            ResultSet rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            c.createStatement().execute(SET_SQL_MODE);
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            c.createStatement().execute(String.format(CREATE_TABLE_SQL, DB_NAME, TABLE_NAME));
            c.createStatement().execute(String.format(USE_DATABASE, DB_NAME));
            rs = c.createStatement().executeQuery(String.format(SHOW_TABLES_SQL, TABLE_NAME + "_%"));
            if (rs.next()) {
                phyTableName = rs.getString(1);
            }
            c.createStatement().execute(String.format(ALTER_TABLE_SQL, phyTableName));
            c.createStatement().execute(String.format(INSERT_SQL, phyTableName, "1"));
        }

        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();
        log.info("start dump from {}:{}", file, pos);
        mysqlConn.dump(file, pos, null, (event, logPosition) -> {
            if (event instanceof WriteRowsLogEvent) {
                WriteRowsLogEvent writeRowsLogEvent = (WriteRowsLogEvent) event;
                if (writeRowsLogEvent.getTable().getTableName().equalsIgnoreCase(TABLE_NAME)) {
                    int columnCnt = writeRowsLogEvent.getTable().getColumnCnt();
                    TableMapLogEvent.ColumnInfo[] columnInfos = writeRowsLogEvent.getTable().getColumnInfo();
                    RowsLogBuffer rowsLogBuffer = writeRowsLogEvent.getRowsBuf("utf-8");
                    BitSet columns = writeRowsLogEvent.getColumns();
                    String[] checkValues = new String[] {"1", "null", "1"};
                    checkRowValues(rowsLogBuffer, columnCnt, columns, columnInfos, checkValues);
                    return false;
                }
            }
            return true;
        });
    }

    public void checkRowValues(RowsLogBuffer rowsLogBuffer, int columnCount, BitSet columns,
                               TableMapLogEvent.ColumnInfo[] columnInfos, String[] checkValues) {
        while (rowsLogBuffer.nextOneRow(columns)) {
            BitSet nullBits = rowsLogBuffer.getNullBits(); // 获取 NULL BITMAP
            for (int i = 0; i < columnCount; i++) {
                TableMapLogEvent.ColumnInfo info = columnInfos[i];
                if (nullBits.get(i)) {
                    log.info("Column {}: NULL", i);
                } else {
                    Serializable value = rowsLogBuffer.nextValue(info.type, info.meta);
                    Assert.assertEquals(checkValues[i], value.toString());
                    log.info("Column {}: {}", i, value);
                }
            }
        }
    }
}
