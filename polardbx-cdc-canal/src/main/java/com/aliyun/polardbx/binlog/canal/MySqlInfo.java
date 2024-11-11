/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
public class MySqlInfo {
    private long serverId;
    private BinlogPosition endPosition;
    private BinlogPosition startPosition;
    private MysqlConnection.BinlogFormat binlogFormat;
    private MysqlConnection.BinlogImage binlogImage;
    private ServerCharactorSet serverCharactorSet;
    private int lowerCaseTableNames;
    private int binlogChecksum = LogEvent.BINLOG_CHECKSUM_ALG_OFF;
    private String sqlMode;

    public void init(MysqlConnection connection) {
        this.serverId = findServerId(connection);
        this.endPosition = findEndPosition(connection);
        this.startPosition = findStartPosition(connection);
        this.binlogFormat = connection.getBinlogFormat();
        this.binlogImage = connection.getBinlogImage();
        this.serverCharactorSet = connection.getDefaultDatabaseCharset();
        this.lowerCaseTableNames = connection.getLowerCaseTableNames();
        this.binlogChecksum = connection.loadBinlogChecksum();
        this.sqlMode = connection.loadSqlMode();
    }

    /**
     * 查询当前db的serverId信息
     */
    private Long findServerId(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show variables like 'server_id'", new MysqlConnection.ProcessJdbcResult<Long>() {

            @Override
            public Long process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    return Long.parseLong(rs.getObject(2).toString());
                } else {
                    throw new CanalParseException(
                        "command : show variables like 'server_id' has an error! pls check. you need (at least one "
                            + "of) the SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    /**
     * 查询当前的binlog位置
     */
    protected BinlogPosition findEndPosition(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show master status", new MysqlConnection.ProcessJdbcResult<BinlogPosition>() {

            @Override
            public BinlogPosition process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    String fileName = rs.getString(1);
                    String position = rs.getString(2);
                    String str = fileName + ':' + position + "#-2.0";
                    return BinlogPosition.parseFromString(str);
                } else {
                    throw new CanalParseException(
                        "command : 'show master status' has an error! pls check. you need (at least one of) the "
                            + "SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    /**
     * 查询当前的binlog位置
     */
    protected BinlogPosition findStartPosition(MysqlConnection mysqlConnection) {
        return mysqlConnection
            .query("show binlog events limit 1", new MysqlConnection.ProcessJdbcResult<BinlogPosition>() {

                @Override
                public BinlogPosition process(ResultSet rs) throws SQLException {
                    if (rs.next()) {
                        String fileName = rs.getString(1);
                        String position = rs.getString(2);
                        String str = fileName + ':' + position + "#-2.0";
                        return BinlogPosition.parseFromString(str);
                    } else {
                        throw new CanalParseException(
                            "command : 'show binlog events limit 1' has an error! pls check. you need (at least one of) "
                                + "the SUPER,REPLICATION CLIENT privilege(s) for this operation");
                    }
                }
            });
    }

}
