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
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Map;

public class PolarxTableMetaProxy implements TableMetaTSDB {

    private Object rplTableMeta;
    private final DataSource dataSource;
    private Method find;
    private Method apply;
    private Method rollback;

    public PolarxTableMetaProxy(DataSource dataSource) {
        this.dataSource = dataSource;
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
