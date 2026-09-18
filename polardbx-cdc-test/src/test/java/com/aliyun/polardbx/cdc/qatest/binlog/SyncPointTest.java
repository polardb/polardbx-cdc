/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * Sync Point 端到端集成测试。
 * <p>
 * 流程：
 * <ol>
 *   <li>SHOW MASTER STATUS 记录 CDC 输出 binlog 的初始位点；</li>
 *   <li>在 CN 上执行 CALL polardbx.trigger_sync_point_trx() 触发 sync point；</li>
 *   <li>创建 token 表并等待下游同步，作为 dump 流的终点标记；</li>
 *   <li>从初始位点流式 dump，直到扫到 token 表事件，期间检查是否出现
 *       Query 中包含 trigger_sync_point_trx 的事件，并以
 *       task_sync_point_enabled 开关作为预期。</li>
 * </ol>
 * <p>
 * 多流（binlogX）模式不运行该用例。
 *
 * @author yudong
 * @since 2024/5/30 16:57
 **/
@Slf4j
public class SyncPointTest extends RplBaseTestCase {

    private static final String SHOW_MASTER_STATUS_SQL = "show master status";

    /**
     * 手动触发 sync point 并通过流式扫描 CDC 输出 binlog 验证开关行为。
     */
    @Test
    @SneakyThrows
    public void testTriggerSyncPointAndVerifyInBinlog() {
        if (usingBinlogX) {
            log.info("multi-stream(binlogX) mode, skip sync point test");
            return;
        }

        boolean syncPointEnabled = isSyncPointEnabled();
        log.info("task_sync_point_enabled: {}", syncPointEnabled);

        // 1. 记录初始位点
        String file;
        long pos;
        try (Connection conn = getPolardbxConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(SHOW_MASTER_STATUS_SQL)) {
            Assert.assertTrue("show master status should return a row", rs.next());
            file = rs.getString("FILE");
            pos = Long.parseLong(rs.getString("POSITION"));
        }
        Assert.assertFalse("show master status returned empty file", StringUtils.isEmpty(file));
        log.info("start dump position: {}:{}", file, pos);

        // 2. 触发 sync point
        try (Connection conn = getPolardbxConnection();
            Statement stmt = conn.createStatement()) {
            log.info("Triggering sync point: CALL polardbx.trigger_sync_point_trx()");
            stmt.execute("CALL polardbx.trigger_sync_point_trx()");
        }

        // 3. 发送 token 并等待下游同步，token 表名作为 dump 流的终点
        String tokenTable = sendTokenAndWaitNamed();

        // 4. 从初始位点流式扫 binlog，遇到 token 表事件即停止
        AtomicBoolean findSyncPoint = new AtomicBoolean(false);
        AtomicBoolean timeOut = new AtomicBoolean(false);
        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();
        try {
            tryFindSyncPointEvent(file, pos, mysqlConn, findSyncPoint, timeOut, tokenTable);
        } finally {
            mysqlConn.disconnect();
        }

        Assert.assertFalse(
            "scan binlog timeout, can not find token event in 5 binlog files start from " + file,
            timeOut.get());
        if (syncPointEnabled) {
            Assert.assertTrue(
                "expect sync point QueryEvent in output binlog when task_sync_point_enabled=true",
                findSyncPoint.get());
        } else {
            Assert.assertFalse(
                "should NOT have sync point QueryEvent when task_sync_point_enabled=false",
                findSyncPoint.get());
        }
    }

    /**
     * 与 RplBaseTestCase#sendTokenAndWait 行为等价，但把 token 表名暴露出来，
     * 用于 binlog dump 的终点判断。仅在单流模式下使用。
     */
    private String sendTokenAndWaitNamed() {
        String uuid = UUID.randomUUID().toString();
        String tableName = TOKEN_TABLE_PREFIX + uuid;
        JdbcUtil.executeSuccess(polardbxConnection, String.format(TOKEN_TABLE_CREATE_SQL, tableName));
        loopWait(tableName, cdcSyncDbConnection, 0L);
        return tableName;
    }

    /**
     * 从指定位点 dump CDC 输出 binlog，按以下规则推进：
     * <ul>
     *   <li>命中 query 含 trigger_sync_point_trx 的 QueryLogEvent → findSyncPoint=true，停止；</li>
     *   <li>命中 query 含 token 表名的 QueryLogEvent → 流终点，正常停止；</li>
     *   <li>跨越 5 个 binlog 文件仍未到终点 → timeOut=true，停止。</li>
     * </ul>
     */
    private void tryFindSyncPointEvent(String file, long pos, MysqlConnection mysqlConn,
                                       AtomicBoolean findSyncPoint, AtomicBoolean timeOut,
                                       String tokenTable) throws Exception {
        int fileSeq = BinlogFileUtil.getBinlogSequence(file);
        mysqlConn.dump(file, pos, null, (event, logPosition) -> {
            if (event instanceof QueryLogEvent) {
                QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                String query = queryLogEvent.getQuery();
                if (query != null && query.contains("trigger_sync_point_trx")) {
                    log.info("find sync point query event at {}:{}, query={}",
                        logPosition.getFileName(), logPosition.getPosition(), query);
                    findSyncPoint.set(true);
                    return false;
                }
                if (query != null && query.contains(tokenTable)) {
                    log.info("reach token table event at {}:{}, stop dump",
                        logPosition.getFileName(), logPosition.getPosition());
                    return false;
                }
            }
            if (event instanceof RotateLogEvent) {
                int seq = BinlogFileUtil.getBinlogSequence(logPosition.getFileName());
                // 跨越 5 个 binlog 文件仍未到终点，链路异常
                if (seq - fileSeq > 5) {
                    timeOut.set(true);
                    return false;
                }
            }
            return true;
        });
    }

    @SneakyThrows
    private boolean isSyncPointEnabled() {
        try (Connection conn = getMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(
                "SELECT config_value FROM binlog_system_config "
                    + "WHERE config_key = 'task_sync_point_enabled'", conn);
            if (rs.next()) {
                return "true".equalsIgnoreCase(rs.getString("config_value"));
            }
        } catch (Exception e) {
            log.warn("Failed to query task_sync_point_enabled", e);
        }
        return false;
    }
}
