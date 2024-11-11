/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.util.PooledHttpHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.entity.ContentType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class StreamHashUtil {
    public static HashLevel DEFAULT_HASH_LEVEL;

    public static HashLevel getHashLevel(String db, String table) {
        String result = request(db, table, "getHashLevel");
        List<String> list = JSONObject.parseArray(result, String.class);
        return HashLevel.valueOf(list.get(0));
    }

    public static int getHashStreamSeq(String db, String table) {
        String result = request(db, table, "getHashStreamSeq");
        List<String> list = JSONObject.parseArray(result, String.class);
        return Integer.parseInt(list.get(0));
    }

    public static HashLevel getDefaultHashLevel() {
        if (DEFAULT_HASH_LEVEL == null) {
            synchronized (StreamHashUtil.class) {
                if (DEFAULT_HASH_LEVEL == null) {
                    DEFAULT_HASH_LEVEL = requestDefaultHashLevel();
                }
            }
        }

        return DEFAULT_HASH_LEVEL;
    }

    private static HashLevel requestDefaultHashLevel() {
        try {
            Pair<String, String> pair = getDaemonInfo();
            String url = String.format("http://%s:%s/config/v1/get/%s",
                pair.getKey(), pair.getValue(), BINLOGX_TRANSMIT_HASH_LEVEL);
            String result = PooledHttpHelper.doGetWithoutParam(url, ContentType.TEXT_PLAIN, 1000);
            return HashLevel.valueOf(result);
        } catch (Throwable t) {
            throw new PolardbxException("get default hash level error!", t);
        }
    }

    @SneakyThrows
    private static String request(String db, String table, String method) {
        long start = System.currentTimeMillis();
        while (true) {
            try {
                Map<String, String> parameter = new HashMap<>();
                parameter.put("db", db);
                parameter.put("table", table);
                Pair<String, String> pair = getDaemonInfo();
                return PooledHttpHelper.doPost("http://" + pair.getKey() + ":" + pair.getValue() + "/tools/" + method,
                    ContentType.APPLICATION_JSON,
                    JSON.toJSONString(parameter), 1000);
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start > 30 * 1000) {
                    throw new PolardbxException("get hash level error , db: " + db + ", table : " + table, e);
                } else {
                    Thread.sleep(1000);
                }
            }
        }
    }

    private static Pair<String, String> getDaemonInfo() throws SQLException {
        try (Connection connection = ConnectionManager.getInstance().getDruidMetaConnection()) {
            JdbcUtil.useDb(connection, PropertiesUtil.getMetaDB);
            Statement stmt = connection.createStatement();
            String sql = "select ip,daemon_port from binlog_node_info where cluster_type = 'BINLOG_X'";
            ResultSet resultSet = stmt.executeQuery(sql);
            if (resultSet.next()) {
                return Pair.of(resultSet.getString("ip"), resultSet.getString("daemon_port"));
            }
        }
        throw new PolardbxException("can`t find available daemon info!");
    }
}
