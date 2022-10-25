/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.fastjson.JSON;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.aliyun.polardbx.binlog.canal.ReplicateFilter;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection.ProcessJdbcResult;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 基于console远程管理
 *
 * @author agapple 2017年7月27日 下午10:47:55
 * @since 3.2.5
 */
public class ConsoleTableMetaTSDB implements TableMetaTSDB {

    public static final BinlogPosition INIT_POSITION = BinlogPosition.parseFromString("0:0#-2.-1");
    private static final Logger logger = LoggerFactory.getLogger(ConsoleTableMetaTSDB.class);
    private String env = null;
    private String serviceName = null;
    private String taskName = null;
    private String consoleDomain = null;
    private int retry = 3;
    private MemoryTableMeta memoryTableMeta;
    private MysqlConnection connection;                                                         // 查询meta信息的链接
    private ReplicateFilter filter;
    private BinlogPosition lastPosition;
    private ScheduledExecutorService scheduler;

    public ConsoleTableMetaTSDB(String serviceName, String taskName, String env, String consoleDomain) {
        this.serviceName = serviceName;
        this.taskName = taskName;
        this.env = env;
        this.consoleDomain = consoleDomain;
        this.memoryTableMeta = new MemoryTableMeta(logger, true);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "[scheduler-table-meta-snapshot]");
            }
        });

        // 24小时生成一份snapshot
        scheduler.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                try {
                    applySnapshotToConsole(lastPosition, false);
                } catch (Throwable e) {
                    logger.error("scheudle faield", e);
                }
            }
        }, 24, 24, TimeUnit.HOURS);
    }

    @Override
    public boolean init(String destination) {
        return false;
    }

    @Override
    public void destory() {

    }

    @Override
    public TableMeta find(String schema, String table) {
        synchronized (memoryTableMeta) {
            return memoryTableMeta.find(schema, table);
        }
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        // 首先记录到内存结构
        synchronized (memoryTableMeta) {
            if (memoryTableMeta.apply(position, schema, ddl, extra)) {
                this.lastPosition = position;
                // 同步每次变更给远程做历史记录
                // return applyHistoryToConsole(position, schema, ddl, extra);
            } else {
                throw new RuntimeException("apply to memory is failed");
            }
        }
        return true;
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        // 每次rollback需要重新构建一次memory data
        this.memoryTableMeta = new MemoryTableMeta(logger, true);
        boolean flag = false;
        BinlogPosition snapshotPosition = buildMemFromSnapshot(position);
        if (snapshotPosition != null) {
            applyHistoryOnMemory(snapshotPosition, position);
            flag = true;
        }

        if (!flag) {
            // 如果没有任何数据，则为初始化状态，全量dump一份关注的表
            if (dumpTableMeta(connection, filter)) {
                // 记录一下snapshot结果,方便快速恢复
                flag = applySnapshotToConsole(INIT_POSITION, true);
            }
        }

        return flag;
    }

    public Map<String, String> snapshot() {
        return memoryTableMeta.snapshot();
    }

    /**
     * 初始化的时候dump一下表结构
     */
    private boolean dumpTableMeta(MysqlConnection connection, final ReplicateFilter filter) {
        List<String> schemas = connection.query("show databases", new ProcessJdbcResult<List>() {

            @Override
            public List process(ResultSet rs) throws SQLException {
                List<String> schemas = new ArrayList<String>();
                while (rs.next()) {
                    String schema = rs.getString(1);
                    if (!filter.filterSchema(schema)) {
                        schemas.add(schema);
                    }
                }
                return schemas;
            }
        });

        for (String schema : schemas) {
            List<String> tables = connection.query("show tables from `" + schema + "`", new ProcessJdbcResult<List>() {

                @Override
                public List process(ResultSet rs) throws SQLException {
                    List<String> tables = new ArrayList<String>();
                    while (rs.next()) {
                        String table = rs.getString(1);
                        if (!filter.filterTable(table)) {
                            tables.add(table);
                        }
                    }
                    return tables;
                }
            });

            if (tables.isEmpty()) {
                continue;
            }

            StringBuilder sql = new StringBuilder();
            for (String table : tables) {
                sql.append("show create table `" + schema + "`.`" + table + "`;");
            }

            // 使用多语句方式读取
            Statement stmt = null;
            try {
                stmt = connection.getConn().createStatement();
                ResultSet rs = stmt.executeQuery(sql.toString());
                boolean existMoreResult = false;
                do {
                    if (existMoreResult) {
                        rs = stmt.getResultSet();
                    }

                    while (rs.next()) {
                        String oneTableCreateSql = rs.getString(2);
                        memoryTableMeta.apply(INIT_POSITION, schema, oneTableCreateSql, null);
                    }

                    existMoreResult = stmt.getMoreResults();
                } while (existMoreResult);
            } catch (SQLException e) {
                throw new CanalParseException(e);
            } finally {
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (SQLException e) {
                        // ignore
                    }
                }
            }
        }

        return true;
    }

    /**
     * 发布数据到console上
     */
    private boolean applySnapshotToConsole(BinlogPosition position, boolean init) {
        // 获取一份快照
        MemoryTableMeta tmpMemoryTableMeta = new MemoryTableMeta(logger, true);
        Map<String, String> schemaDdls = null;
        synchronized (memoryTableMeta) {
            if (!init && position == null) {
                // 如果是持续构建,则识别一下是否有DDL变更过,如果没有就忽略了
                return false;
            }
            schemaDdls = memoryTableMeta.snapshot();
            for (Map.Entry<String, String> entry : schemaDdls.entrySet()) {
                tmpMemoryTableMeta.apply(position, entry.getKey(), entry.getValue(), null);
            }
        }

        // 基于临时内存对象进行对比
        boolean compareAll = true;
        for (Schema schema : tmpMemoryTableMeta.getRepository().getSchemas()) {
            for (String table : schema.showTables()) {
                if (!compareTableMetaDbAndMemory(connection, tmpMemoryTableMeta, schema.getName(), table)) {
                    compareAll = false;
                }
            }
        }
        if (compareAll) {
            String url = consoleDomain + "/open/canalTableMeta/snapshot?env=" + env;
            Map<String, String> params = new HashMap<String, String>();
            params.put("taskName", taskName);
            Map<String, String> content = new HashMap<String, String>();
            content.put("env", env);
            content.put("serviceName", serviceName);
            content.put("taskName", taskName);
            content.put("binlogFile", position.getFileName());
            content.put("binlogOffest", String.valueOf(position.getPosition()));
            content.put("binlogMasterId", String.valueOf(position.getMasterId()));
            content.put("binlogTimestamp", String.valueOf(position.getTimestamp()));
            content.put("data", JSON.toJSONString(schemaDdls));
            params.put("content", JSON.toJSONString(content));
            for (int i = 0; i < retry; i++) {
                try {
                    // 3秒超时
                    // String result = HttpHelper.post(url, null, params, 3000, null);
                    // JSONObject object = (JSONObject) JSON.parse(result);
                    // Object code = object.get("code");
                    // Object message = object.get("msg");
                    // String f = String.valueOf(JingWeiConstants.FAILURE_CODE);
                    // String c = (code == null ? "-1" : code.toString());
                    // if (StringUtils.equals(f, c)) {
                    // throw new RuntimeException("apply failed caused by : " + message);
                    // }

                    return true;
                } catch (Throwable e) {
                    if (i == retry - 1) {
                        throw new RuntimeException("apply failed", e);
                    } else {
                        logger.warn("apply retry : " + (i + 1), e);
                    }
                }
            }

            return false;
        } else {
            logger.error("compare failed , check log");
        }
        return false;
    }

    private boolean compareTableMetaDbAndMemory(MysqlConnection connection, MemoryTableMeta memoryTableMeta,
                                                final String schema, final String table) {
        TableMeta tableMetaFromDB = connection.query("show create table " + getFullName(schema, table),
            new ProcessJdbcResult<TableMeta>() {

                @Override
                public TableMeta process(ResultSet rs) throws SQLException {
                    String createDDL = null;
                    while (rs.next()) {
                        createDDL = rs.getString(2);
                    }

                    MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, true);
                    memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, schema, createDDL, null);
                    TableMeta tableMeta = memoryTableMeta.find(schema, table);
                    return tableMeta;
                }
            });

        TableMeta tableMetaFromMem = memoryTableMeta.find(schema, table);
        boolean result = compareTableMeta(tableMetaFromMem, tableMetaFromDB);
        if (!result) {
            logger.error("compare failed . \n db : " + tableMetaFromDB + " \n mem : " + tableMetaFromMem);
        }

        return result;
    }

    private BinlogPosition buildMemFromSnapshot(BinlogPosition position) {
        String url = consoleDomain + "/open/canalTableMeta/findSnapshot?env=" + env;
        Map<String, String> params = new HashMap<String, String>();
        params.put("taskName", taskName);

        Map<String, String> content = new HashMap<String, String>();
        content.put("env", env);
        content.put("serviceName", serviceName);
        content.put("taskName", taskName);
        content.put("binlogFile", position.getFileName());
        content.put("binlogOffest", String.valueOf(position.getPosition()));
        content.put("binlogMasterId", String.valueOf(position.getMasterId()));
        content.put("binlogTimestamp", String.valueOf(position.getTimestamp()));
        params.put("content", JSON.toJSONString(content));
        for (int i = 0; i < retry; i++) {
            try {
                // 3秒超时
                // String result = HttpHelper.post(url, null, params, 3000, null);
                // JSONObject object = (JSONObject) JSON.parse(result);
                // Object code = object.get("code");
                // Object message = object.get("msg");
                // String f = String.valueOf(JingWeiConstants.SUCCESS_CODE);
                // String c = (code == null ? "1" : code.toString());
                // if (StringUtils.equals(f, c)) {
                // String data = ObjectUtils.toString(object.get("content"));
                // JSONObject jsonData = JSON.parseObject(data);
                // if (jsonData == null) {
                // // 可能没有任何snapshot数据
                // return null;
                // }
                //
                // String binlogFile = jsonData.getString("binlogFile");
                // String binlogOffest = jsonData.getString("binlogOffest");
                // String binlogMasterId = jsonData.getString("binlogMasterId");
                // String binlogTimestamp = jsonData.getString("binlogTimestamp");
                //
                // BinlogPosition snapshotPosition = new BinlogPosition(binlogFile,
                // Long.valueOf(binlogOffest == null ? "0" : binlogOffest),
                // Long.valueOf(binlogMasterId == null ? "-2" : binlogMasterId),
                // Long.valueOf(binlogTimestamp == null ? "0" : binlogTimestamp));
                // // data存储为Map<String,String>，每个分库一套建表
                // String sqlData = jsonData.getString("data");
                // JSONObject jsonObj = JSON.parseObject(sqlData);
                // for (Map.Entry entry : jsonObj.entrySet()) {
                // // 记录到内存
                // if (!memoryTableMeta.apply(snapshotPosition,
                // ObjectUtils.toString(entry.getKey()),
                // ObjectUtils.toString(entry.getValue()),
                // null)) {
                // return null;
                // }
                // }
                //
                // return snapshotPosition;
                // } else {
                // throw new RuntimeException("apply failed caused by : " + message);
                // }
            } catch (Throwable e) {
                if (i == retry - 1) {
                    throw new RuntimeException("apply failed", e);
                } else {
                    logger.warn("apply retry : " + (i + 1), e);
                }
            }
        }

        return null;
    }

    private boolean applyHistoryOnMemory(BinlogPosition position, BinlogPosition rollbackPosition) {
        String url = consoleDomain + "/open/canalTableMeta/findHistory?env=" + env;
        Map<String, String> params = new HashMap<String, String>();
        params.put("taskName", taskName);

        Map<String, String> content = new HashMap<String, String>();
        content.put("env", env);
        content.put("serviceName", serviceName);
        content.put("taskName", taskName);
        content.put("binlogSnapshotTimestamp", String.valueOf(position.getTimestamp()));
        content.put("binlogFile", rollbackPosition.getFileName());
        content.put("binlogOffest", String.valueOf(rollbackPosition.getPosition()));
        content.put("binlogMasterId", String.valueOf(rollbackPosition.getMasterId()));
        content.put("binlogTimestamp", String.valueOf(rollbackPosition.getTimestamp()));
        params.put("content", JSON.toJSONString(content));
        for (int i = 0; i < retry; i++) {
            try {
                // 3秒超时
                // String result = HttpHelper.post(url, null, params, 3000, null);
                // JSONObject object = (JSONObject) JSON.parse(result);
                // Object code = object.get("code");
                // Object message = object.get("msg");
                // String f = String.valueOf(JingWeiConstants.SUCCESS_CODE);
                // String c = (code == null ? "1" : code.toString());
                // if (StringUtils.equals(f, c)) {
                // String data = ObjectUtils.toString(object.get("content"));
                // JSONArray jsonArray = JSON.parseArray(data);
                // for (Object jsonObj : jsonArray) {
                // JSONObject jsonData = (JSONObject) jsonObj;
                // String binlogFile = jsonData.getString("binlogFile");
                // String binlogOffest = jsonData.getString("binlogOffest");
                // String binlogMasterId = jsonData.getString("binlogMasterId");
                // String binlogTimestamp = jsonData.getString("binlogTimestamp");
                // String useSchema = jsonData.getString("useSchema");
                // String sqlData = jsonData.getString("sql");
                // BinlogPosition snapshotPosition = new BinlogPosition(binlogFile,
                // Long.valueOf(binlogOffest == null ? "0" : binlogOffest),
                // Long.valueOf(binlogMasterId == null ? "-2" : binlogMasterId),
                // Long.valueOf(binlogTimestamp == null ? "0" : binlogTimestamp));
                //
                // // 如果是同一秒内,对比一下history的位点，如果比期望的位点要大，忽略之
                // if (snapshotPosition.getTimestamp() > rollbackPosition.getTimestamp()) {
                // continue;
                // } else if (rollbackPosition.getMasterId() == snapshotPosition.getMasterId()
                // && snapshotPosition.compareTo(rollbackPosition) > 0) {
                // continue;
                // }
                //
                // // 记录到内存
                // if (!memoryTableMeta.apply(snapshotPosition, useSchema, sqlData, null)) {
                // return false;
                // }
                //
                // }
                // return jsonArray.size() > 0;
                // } else {
                // throw new RuntimeException("apply failed caused by : " + message);
                // }
            } catch (Throwable e) {
                if (i == retry - 1) {
                    throw new RuntimeException("apply failed", e);
                } else {
                    logger.warn("apply retry : " + (i + 1), e);
                }
            }
        }

        return false;
    }

    private String getFullName(String schema, String table) {
        StringBuilder builder = new StringBuilder();
        return builder.append('`')
            .append(schema)
            .append('`')
            .append('.')
            .append('`')
            .append(table)
            .append('`')
            .toString();
    }

    private boolean compareTableMeta(TableMeta source, TableMeta target) {
        if (!StringUtils.equalsIgnoreCase(source.getSchema(), target.getSchema())) {
            return false;
        }

        if (!StringUtils.equalsIgnoreCase(source.getTable(), target.getTable())) {
            return false;
        }

        List<FieldMeta> sourceFields = source.getFields();
        List<FieldMeta> targetFields = target.getFields();
        if (sourceFields.size() != targetFields.size()) {
            return false;
        }

        for (int i = 0; i < sourceFields.size(); i++) {
            FieldMeta sourceField = sourceFields.get(i);
            FieldMeta targetField = targetFields.get(i);
            if (!StringUtils.equalsIgnoreCase(sourceField.getColumnName(), targetField.getColumnName())) {
                return false;
            }

            if (!StringUtils.equalsIgnoreCase(sourceField.getColumnType(), targetField.getColumnType())) {
                return false;
            }

            if (!StringUtils.equalsIgnoreCase(sourceField.getDefaultValue(), targetField.getDefaultValue())) {
                return false;
            }

            if (sourceField.isNullable() != targetField.isNullable()) {
                return false;
            }

            // mysql会有一种处理,针对show create只有uk没有pk时，会在desc默认将uk当做pk
            boolean isSourcePkOrUk = sourceField.isKey() || sourceField.isUnique();
            boolean isTargetPkOrUk = targetField.isKey() || targetField.isUnique();
            if (isSourcePkOrUk != isTargetPkOrUk) {
                return false;
            }
        }

        return true;
    }

    public void setFilter(ReplicateFilter filter) {
        this.filter = filter;
    }

    public MysqlConnection getConnection() {
        return connection;
    }

    public void setConnection(MysqlConnection connection) {
        this.connection = connection;
    }

    public MemoryTableMeta getMemoryTableMeta() {
        return memoryTableMeta;
    }

}
