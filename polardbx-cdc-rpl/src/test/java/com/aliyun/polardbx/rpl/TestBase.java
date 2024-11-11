/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.RplCommandResponse;
import com.aliyun.polardbx.rpc.cdc.StartSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.StopSlaveRequest;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.HostManager;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.aliyun.polardbx.rpl.taskmeta.RplServiceManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/2/23 20:38
 * @since 5.0.0.0
 */
@Ignore
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class TestBase {
    protected final int WAIT_DML_SECOND = 1;
    protected final int WAIT_DDL_SECOND = 4;
    protected final int WAIT_TASK_SECOND = 5;

    protected String channel = "testBase";

    protected HostInfo srcHostInfo;
    protected HostInfo dstHostInfo;
    protected HostInfo mysqlDstHostInfo;

    protected DataSource srcDs;
    protected DataSource dstDs;
    protected DataSource mysqlDstDs;

    protected Connection srcConn;
    protected Connection dstConn;
    protected Connection mysqlDstConn;

    protected BinlogPosition initBinlogPosition;

    protected RplTaskRunner rplTaskRunner;
    protected Thread runnerThread;

    @Before
    public void before() throws Exception {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();

        // 创建任务，建连，创建库表
        initConnection();
        initService();
    }

    @After
    public void after() throws Exception {
        // 删除库，关闭连接，停和删任务
        new Thread(() -> rplTaskRunner.stop()).start();
        // 触发 com/aliyun/polardbx/binlog/canal/core/AbstractEventParser.java 中的
        // parseThread 进入下一次循环以检测到 running == false 从而退出
        String someDb = "some_db";
        while (!rplTaskRunner.getExtractor().isRunning()) {
            execUpdate(srcConn, "create database if not exists " + someDb, null);
            execUpdate(srcConn, "drop database " + someDb, null);
            wait(1);
        }
    }

    protected void initConnection() throws Exception {
        srcHostInfo = new HostInfo();
        srcHostInfo.setHost("127.0.0.1");
        srcHostInfo.setPort(3306);
        srcHostInfo.setUserName("root");
        srcHostInfo.setPassword("polarx_test");

        mysqlDstHostInfo = new HostInfo();
        mysqlDstHostInfo.setHost("127.0.0.1");
        mysqlDstHostInfo.setPort(3307);
        mysqlDstHostInfo.setUserName("root");
        mysqlDstHostInfo.setPassword("polarx_test");

        dstHostInfo = HostManager.getDstPolarxHost();
        dstHostInfo.setUsePolarxPoolCN(true);

        srcDs = createDataSource(srcHostInfo, "");
        dstDs = createDataSource(dstHostInfo, "");
        mysqlDstDs = createDataSource(mysqlDstHostInfo, "");

        srcConn = srcDs.getConnection();
        dstConn = dstDs.getConnection();
        mysqlDstConn = mysqlDstDs.getConnection();
    }

    protected void initService() throws Exception {
        initBinlogPosition = getLastBinlogPosition();
        System.out.println("last binlog position: " + JSON.toJSONString(initBinlogPosition));

        // setup service
        setupService(channel, initBinlogPosition, null);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        rplTaskRunner = new RplTaskRunner(getTaskId(channel));
        runnerThread = new Thread(() -> rplTaskRunner.start());
    }

    protected BinlogPosition getLastBinlogPosition() throws Exception {
        List<Map<String, String>> result = execQuery(srcConn,
            "show master status",
            Arrays.asList("File", "Position"));
        String file = result.get(0).get("File");
        long position = Long.valueOf(result.get(0).get("Position"));
        return new BinlogPosition(file, position, -1, -1);
    }

    protected void setupService(String channel, BinlogPosition position,
                                Map<String, String> filterParams) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(RplConstants.MASTER_HOST, srcHostInfo.getHost());
        params.put(RplConstants.MASTER_PORT, String.valueOf(srcHostInfo.getPort()));
        params.put(RplConstants.MASTER_USER, srcHostInfo.getUserName());
        params.put(RplConstants.MASTER_PASSWORD, srcHostInfo.getPassword());
        params.put(RplConstants.MASTER_LOG_FILE, position.getFileName());
        params.put(RplConstants.MASTER_LOG_POS, String.valueOf(position.getPosition()));
        params.put(RplConstants.CHANNEL, channel);
        params.put(RplConstants.SOURCE_HOST_TYPE, "rds");

        RplServiceManager.stopSlave(params);
        RplServiceManager.changeMaster(params);

        if (filterParams == null) {
            filterParams = new HashMap<>();
            filterParams.put(RplConstants.REPLICATE_DO_DB, "");
            filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
            filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
            filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
            filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
            filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
            filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        }
        filterParams.put(RplConstants.CHANNEL, channel);
        RplServiceManager.changeReplicationFilter(params);
        RplServiceManager.startSlave(params);
    }

    protected void setupMysqlReplica(Connection mysqlDstConn, BinlogPosition position) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        execUpdate(mysqlDstConn, "stop slave", null);
        execUpdate(mysqlDstConn, getChangeMasterSql(srcHostInfo, position), null);
        execUpdate(mysqlDstConn, printChangeFilterSql(replicaMeta), null);
        execUpdate(mysqlDstConn, "start slave", null);
    }

    private String getChangeMasterSql(HostInfo srcHostInfo, BinlogPosition position) {
        String sql = String
            .format("CHANGE MASTER TO\n" + "MASTER_HOST='%s',\n" + "MASTER_USER='%s',\n" + "MASTER_PORT=%d,\n"
                    + "MASTER_PASSWORD='%s',\n" + "MASTER_LOG_FILE='%s',\n" + "MASTER_LOG_POS=%d;",
                srcHostInfo.getHost(),
                srcHostInfo.getUserName(),
                srcHostInfo.getPort(),
                srcHostInfo.getPassword(),
                position.getFileName(),
                position.getPosition());
        return sql;
    }

    protected long getTaskId(String channel) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        RplService service = DbTaskMetaManager.getService(stateMachine.getId(), ServiceType.REPLICA_INC);
        return DbTaskMetaManager.listTaskByService(service.getId()).get(0).getId();
    }

    protected void wait(int seconds) throws Exception {
        Thread.sleep(seconds * 1000);
    }

    protected StreamObserver<RplCommandResponse> fakeStreamObserver() {
        return new StreamObserver<RplCommandResponse>() {

            @Override
            public void onNext(RplCommandResponse rplCommandResponse) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }

    protected DataSource createDataSource(HostInfo hostInfo, String db) {
        try {
            return DataSourceUtil.createDruidMySqlDataSource(hostInfo.isUsePolarxPoolCN(),
                hostInfo.getHost(),
                hostInfo.getPort(),
                db,
                hostInfo.getUserName(),
                hostInfo.getPassword(),
                "utf8mb4",
                1,
                60,
                true,
                null,
                null);
        } catch (Throwable e) {
            System.out.println(e);
        }
        return null;
    }

    protected boolean execUpdate(DataSource dataSource, String sql, List<Serializable> params) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);

            // set value
            int i = 1;
            if (params != null) {
                for (Serializable dataValue : params) {
                    stmt.setObject(i, dataValue);
                    i++;
                }
            }

            // execute
            stmt.executeUpdate();
            return true;
        } catch (Throwable e) {
            log.error("failed in execUpdate: " + sql, e);
            return false;
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
    }

    protected boolean execUpdate(Connection connection, String sql, List<Serializable> params) {
        PreparedStatement stmt = null;

        try {
            stmt = connection.prepareStatement(sql);

            // set value
            int i = 1;
            if (params != null) {
                for (Serializable dataValue : params) {
                    stmt.setObject(i, dataValue);
                    i++;
                }
            }

            // execute
            stmt.executeUpdate();
            return true;
        } catch (Throwable e) {
            log.error("failed in execUpdate: " + sql, e);
            return false;
        } finally {
            DataSourceUtil.closeQuery(null, stmt, null);
        }
    }

    protected List<List<String>> execQuery(DataSource dataSource, String sql, List<String> fields) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        List<List<String>> result = new ArrayList<>();

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);

            res = stmt.executeQuery();
            while (res.next()) {
                List<String> record = new ArrayList<>();
                for (String field : fields) {
                    record.add(res.getString(field));
                }
                result.add(record);
            }
        } catch (Throwable e) {
            log.error("failed in execQuery: " + sql, e);
        } finally {
            DataSourceUtil.closeQuery(res, stmt, conn);
        }

        return result;
    }

    protected List<Map<String, String>> execQuery(Connection connection, String sql, List<String> fields) {
        PreparedStatement stmt = null;
        ResultSet res = null;
        List<Map<String, String>> result = new ArrayList<>();

        try {
            stmt = connection.prepareStatement(sql);

            res = stmt.executeQuery();
            while (res.next()) {
                Map<String, String> record = new HashMap<>();
                for (String field : fields) {
                    record.put(field, res.getString(field));
                }
                result.add(record);
            }
        } catch (Throwable e) {
            log.error("failed in execQuery: " + sql, e);
        } finally {
            DataSourceUtil.closeQuery(res, stmt, null);
        }

        return result;
    }

    protected List<String> execQuery(Connection connection, String sql, int fieldIndex) {
        PreparedStatement stmt = null;
        ResultSet res = null;
        List<String> result = new ArrayList<>();

        try {
            stmt = connection.prepareStatement(sql);

            res = stmt.executeQuery();
            while (res.next()) {
                result.add(res.getString(fieldIndex));
            }
        } catch (Throwable e) {
            log.error("failed in execQuery: " + sql, e);
        } finally {
            DataSourceUtil.closeQuery(res, stmt, null);
        }

        return result;
    }

    protected String printChangeFilterSql(ReplicaMeta replicaMeta) {
        String wildDoTable = adjustWildFilter(replicaMeta.getWildDoTable());
        wildDoTable = StringUtils.isNotBlank(wildDoTable) ? wildDoTable : "";
        String wildIgnoreTable = adjustWildFilter(replicaMeta.getWildIgnoreTable());
        wildIgnoreTable = StringUtils.isNotBlank(wildIgnoreTable) ? wildIgnoreTable : "";

        String sql = String.format("CHANGE REPLICATION FILTER \n" + "REPLICATE_DO_DB=(%s),\n"
                + "REPLICATE_IGNORE_DB=(%s),\n" + "REPLICATE_DO_TABLE=(%s),\n"
                + "REPLICATE_IGNORE_TABLE=(%s),\n" + "REPLICATE_WILD_DO_TABLE=(%s),\n"
                + "REPLICATE_WILD_IGNORE_TABLE=(%s),\n" + "REPLICATE_REWRITE_DB=(%s);",
            StringUtils.isNotBlank(replicaMeta.getDoDb()) ? replicaMeta.getDoDb() : "",
            StringUtils.isNotBlank(replicaMeta.getIgnoreDb()) ? replicaMeta.getIgnoreDb() : "",
            StringUtils.isNotBlank(replicaMeta.getDoTable()) ? replicaMeta.getDoTable() : "",
            StringUtils.isNotBlank(replicaMeta.getIgnoreTable()) ? replicaMeta.getIgnoreTable() : "",
            wildDoTable,
            wildIgnoreTable,
            replicaMeta.getRewriteDb());
        System.out.println("stop slave;");
        System.out.println(sql);
        System.out.println("start slave;");
        return sql;
    }

    protected boolean checkFinish(List<Future> futures) throws Exception {
        boolean finish = false;
        while (!finish) {
            finish = true;
            for (Future future : futures) {
                if (!future.isDone()) {
                    finish = false;
                    break;
                }
            }
            Thread.sleep(1000);
        }

        return finish;
    }

    protected void checkTwoDstsSame(String db, String tb, List<String> fields, String orderByField) {
        String sql = String.format("select * from %s.%s order by %s", db, tb, orderByField);
        List<Map<String, String>> dstRes = execQuery(dstConn, sql, fields);
        List<Map<String, String>> mysqlDstRes = execQuery(mysqlDstConn, sql, fields);

        Assert.assertEquals(dstRes.size(), mysqlDstRes.size());
        for (int i = 0; i < dstRes.size(); i++) {
            Map<String, String> dstRecord = dstRes.get(i);
            Map<String, String> mysqlDstRecord = mysqlDstRes.get(i);
            for (String field : fields) {
                Assert.assertEquals(dstRecord.get(field), mysqlDstRecord.get(field));
            }
        }
    }

    private DefaultRowChange createRowChange(String schema, String table, DBMSAction action, int f0, int f1,
                                             int f2, int f3) {
        return createRowChange(schema, table, action, f0, f1, f2, f3, -1, -1, -1, -1);
    }

    protected DefaultRowChange createRowChange(String schema, String table, DBMSAction action, int f0B, int f1B,
                                               int f2B, int f3B, int f0A, int f1A,
                                               int f2A, int f3A) {
        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setSchema(schema);
        rowChange.setTable(table);
        rowChange.setAction(action);

        List<DBMSRowData> dataSet = new ArrayList<>();
        DefaultRowData rowData = new DefaultRowData();
        rowData.setRowValue(0, f0B);
        rowData.setRowValue(1, f1B);
        rowData.setRowValue(2, f2B);
        rowData.setRowValue(3, f3B);
        dataSet.add(rowData);
        rowChange.setDataSet(dataSet);

        if (action == DBMSAction.UPDATE) {
            List<DBMSRowData> changeDataSet = new ArrayList<>();
            DefaultRowData changeRowData = new DefaultRowData();
            changeRowData.setRowValue(0, f0A);
            changeRowData.setRowValue(1, f1A);
            changeRowData.setRowValue(2, f2A);
            changeRowData.setRowValue(3, f3A);
            changeDataSet.add(changeRowData);
            rowChange.setChangeDataSet(changeDataSet);
        }

        return rowChange;
    }

    private String adjustWildFilter(String wildFilter) {
        String[] tokens = wildFilter.split(",");
        for (int i = 0; i < tokens.length; i++) {
            if (StringUtils.isNotBlank(tokens[i])) {
                tokens[i] = "'" + tokens[i].trim() + "'";
            }
        }
        return StringUtils.join(tokens, ",");
    }
}
