/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;

public class DNStorageSqlExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DNStorageSqlExecutor.class);
    private final DnHost dnHost;
    private final String storageInstanceId;

    public DNStorageSqlExecutor(String storageInstanceId) {
        dnHost = DnHost.getNormalDnHost(storageInstanceId);
        this.storageInstanceId = storageInstanceId;
    }

    public void executeUpdate(String sql) throws SQLException {
        String url = "jdbc:mysql://" + dnHost.getIp() + ":"
            + dnHost.getPort() + "?allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, dnHost.getUserName(), dnHost.getPassword())) {
            Statement st = conn.createStatement();
            st.executeUpdate(sql);
            logger.info("execute sql : " + sql + " on : " + storageInstanceId + JSON.toJSONString(dnHost));
        }
    }

    public List<LinkedHashMap<String, Object>> executeQuery(String sql) throws SQLException {
        String url = "jdbc:mysql://" + dnHost.getIp() + ":"
            + dnHost.getPort() + "?allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false";
        List<LinkedHashMap<String, Object>> result = Lists.newArrayList();
        try (Connection conn = DriverManager.getConnection(url, dnHost.getUserName(), dnHost.getPassword())) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int count = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                LinkedHashMap<String, Object> valueMap = Maps.newLinkedHashMap();
                for (int i = 0; i < count; i++) {
                    valueMap.put(
                        rs.getMetaData().getColumnName(i + 1),
                        rs.getObject(i + 1));
                }
                result.add(valueMap);
            }
        }
        return result;
    }

    public void tryFlushDnBinlog() {
        try {
            executeUpdate("flush logs");
            LabEventManager
                .logEvent(LabEventType.FLUSH_DN_LOG_WHEN_START_TASK, "dnInstId:" + storageInstanceId);
        } catch (SQLException ex) {
            logger.error("flush logs failed! ", ex);
        }
    }
}
