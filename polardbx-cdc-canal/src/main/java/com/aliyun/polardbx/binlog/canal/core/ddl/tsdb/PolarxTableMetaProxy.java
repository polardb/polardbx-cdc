/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Map;

@Slf4j
public class PolarxTableMetaProxy implements TableMetaTSDB {

    private Object rplTableMeta;
    private final DataSource dataSource;
    private Method find;
    private Method apply;
    private Method rollback;

    public PolarxTableMetaProxy(DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("polardbx table meta proxy is created");
    }

    @Override
    @SuppressWarnings("all")
    public boolean init(String destination) {
        try {
            Class cls = Class.forName("com.aliyun.polardbx.binlog.cdc.meta.RplTableMetaManager");
            Connection conn = dataSource.getConnection();
            Constructor constructor = cls.getConstructor(Connection.class);
            rplTableMeta = constructor.newInstance(conn);
            find = cls.getDeclaredMethod("getTableMeta", String.class, String.class);
            apply = cls.getDeclaredMethod("apply", BinlogPosition.class, String.class, String.class);
            rollback = cls.getDeclaredMethod("rollback", String.class);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        return false;
    }

    @Override
    public void destroy() {

    }

    @Override
    public TableMeta find(String schema, String table) {
        try {
            return (TableMeta) find.invoke(rplTableMeta, schema, table);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        try {
            apply.invoke(rplTableMeta, position, schema, ddl);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        return true;
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        try {
            rollback.invoke(rplTableMeta, position.getRtso());
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        return true;
    }

    @Override
    public Map<String, String> snapshot() {
        return null;
    }
}
