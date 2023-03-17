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
package com.aliyun.polardbx.cdc.qatest.meta;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TableMetaSnapBuild extends BaseTestCase {

    private String sharedTableDDL = "CREATE TABLE `tt_shard_%d` (\n"
        + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
        + "\t`name` varchar(20) DEFAULT NULL,\n"
        + "\tPRIMARY KEY (`id`)\n"
        + ") dbpartition by hash(id) tbpartition by hash(id) tbpartitions 32";

    private String getCmdId() throws SQLException {
        Connection metaConn = getMetaConnection();
        try {
            log.info("begin to query meta command id");
            Statement st = metaConn.createStatement();
            // 先 在command 表插入  , 当 logicHistory
            ResultSet rs = st.executeQuery(
                "select * from binlog_polarx_command where cmd_type = 'BUILD_META_SNAPSHOT' order by id desc limit 1");
            if (!rs.next()) {
                log.info("query meta command id failed, command not send to command table!");
                return null;
            }
            int status = rs.getInt("cmd_status");
            if (status == 2) {
                String reply = rs.getString("cmd_reply");
                log.error("query meta command id failed with reply " + reply);
                throw new PolardbxException("process command failed ! " + reply);
            }
            if (status == 0) {
                log.warn("query meta command id return 0, not finish!");
                return null;
            }
            String cmdId = rs.getString("cmd_id");
            log.warn("query meta command success! cmd id : " + cmdId);
            return cmdId;
        } finally {
            metaConn.close();
        }

    }

    public String checkLogicHistory(String cmd) throws SQLException {
        Connection metaConn = getMetaConnection();
        try {
            log.info("begin query logic meta history from cmd " + cmd);

            Statement st = metaConn.createStatement();
            // 先 在command 表插入  , 当 logicHistory
            ResultSet rs = st.executeQuery(
                "select * from binlog_logic_meta_history  where instruction_id = '" + cmd + "'");
            if (!rs.next()) {
                log.warn("query logic meta history from cmd failed!" + cmd);
                return null;
            }
            String tso = rs.getString("tso");
            log.info("query logic meta history from cmd success cmd:" + cmd + " tso : " + tso);
            return tso;
        } finally {
            metaConn.close();
        }
    }

    public boolean checkPhyHistoryCleaner(String tso) throws SQLException {
        Connection metaConn = getMetaConnection();
        try {
            log.info("begin check phy meta history clean result from tso " + tso);
            Statement st = metaConn.createStatement();
            // 先 在command 表插入  , 当 logicHistory
            ResultSet rs = st.executeQuery(
                "select count(*) as ddl_count from binlog_phy_ddl_history  where tso < '" + tso + "'");
            if (!rs.next()) {
                log.warn("query phy meta history clean result failed ! from tso " + tso);
                return false;
            }
            int count = rs.getInt("ddl_count");
            log.info(" phy meta history clean success ! from tso " + tso + " count : " + count);
            return count == 0;
        } finally {
            metaConn.close();
        }
    }

    public boolean checkLogicHistoryCleaner(String tso) throws SQLException {
        Connection metaConn = getMetaConnection();
        try {
            log.info("begin query logic meta history clean result from tso " + tso);
            Statement st = metaConn.createStatement();
            // 先 在command 表插入  , 当 logicHistory
            ResultSet rs = st.executeQuery(
                "select count(*) as ddl_count from binlog_logic_meta_history  where tso < '" + tso + "'");
            if (!rs.next()) {
                log.warn("query logic meta history clean result failed ! from tso " + tso);
                return false;
            }
            int count = rs.getInt("ddl_count");
            log.info(" logic meta history clean result success ! from tso " + tso + " count : " + count);
            return count == 0;
        } finally {
            metaConn.close();
        }
    }

    public void testSnapBuild() throws SQLException, ExecutionException, RetryException {
        // 这块逻辑需要cn 和  cdc联合处理，超时时间给的长一点5分钟
        Retryer<String>
            stringRetryer =
            RetryerBuilder.<String>newBuilder().withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS))
                .withStopStrategy(
                    StopStrategies.stopAfterAttempt(30)).retryIfResult(Objects::isNull).build();
        // 获取cmdId 指令
        String cmdId = stringRetryer.call(() -> getCmdId());
        if (StringUtils.isBlank(cmdId)) {
            throw new PolardbxException("fetch binlog_polarx_command snap cmd id failed!");
        }
        // 指令成功后， 尝试获取LogicDDL表中生成的snap 记录
        String tso = stringRetryer.call(() -> checkLogicHistory(cmdId));

        if (StringUtils.isBlank(tso)) {
            throw new PolardbxException("fetch logic history command id tso failed! cmdId : " + cmdId);
        }
        // 超时时间30s足够了
        Retryer<Boolean>
            booleanRetryer =
            RetryerBuilder.<Boolean>newBuilder().withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS))
                .withStopStrategy(
                    StopStrategies.stopAfterAttempt(6)).retryIfResult(p -> !p).build();
        // 检查物理历史DDL是否清理成功
        if (!booleanRetryer.call(() -> checkPhyHistoryCleaner(tso))) {
            throw new PolardbxException("check phy history clean failed! with tso : " + tso);
        }
        // 检查逻辑DDL是否清理成功
        if (!booleanRetryer.call(() -> checkLogicHistoryCleaner(tso))) {
            throw new PolardbxException("check logic history clean failed! with tso : " + tso);
        }

    }

    public boolean isSendCmd() throws SQLException {
        Connection metaConn = getMetaConnection();
        try {
            log.info("begin to query meta command id");
            Statement st = metaConn.createStatement();
            // 先 在command 表插入  , 当 logicHistory
            ResultSet rs = st.executeQuery(
                "select count(*) as snap_count from binlog_polarx_command where cmd_type = 'BUILD_META_SNAPSHOT' and cmd_status = 0");
            if (!rs.next()) {
                log.info("query meta command id failed, command not send to command table!");
                return false;
            }
            return rs.getInt("snap_count") > 0;
        } finally {
            metaConn.close();
        }
    }

    @Test
    public void test() throws SQLException, ExecutionException, RetryException {
        log.info("begin create shard table !");
        Connection connection = getPolardbxConnection();
        try {
            Statement st = connection.createStatement();
            // 2 * 32 = 64张表，会触发多次clean
            for (int i = 0; i < 20; i++) {
                String ddl = String.format(sharedTableDDL, i);
                log.info("execute : " + ddl);
                st.executeUpdate(ddl);
                // 检测到执行command
                if (isSendCmd()) {
                    log.info("detected already send build snap command! begin to test");
                    testSnapBuild();
                }
            }
            log.info("success create shard table !");
        } finally {
            connection.close();
        }
    }
}
