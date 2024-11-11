/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.MySqlInfo;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class MetaDbHelper {

    private static final String QUERY_ONE_VIP_STORAGE =
        "select * from storage_info where inst_kind=0 and is_vip = 1 and status != 2 limit 1";
    private static final String QUERY_BASE_SNAPSHOT =
        "select tso , topology from binlog_logic_meta_history where type = 1 order by tso desc limit 1";

    private static final String QUERY_BASE_SNAPSHOT_WITH_TSO =
        "select tso , topology from binlog_logic_meta_history where type = 1 and tso <= '%s' order by tso desc limit 1";

    private static final String QUERY_HISTORY_DDL_BY_PAGE =
        "select tso , db_name, ddl from binlog_logic_meta_history where tso > '%s' and tso <= '%s' and type = 2 order by tso asc limit %s";

    private static final String QUERY_DUMPER_MASTER_INFO = " select ip, port from binlog_dumper_info where role = 'M'";

    private IMetaDBDataSourceProvider provider;

    public MetaDbHelper(IMetaDBDataSourceProvider provider) {
        this.provider = provider;
    }

    private List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        Connection connection = provider.getConnection();
        try {
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(sql);
            ResultSetMetaData metaData = rs.getMetaData();
            List<Map<String, Object>> dataList = Lists.newArrayList();
            while (rs.next()) {
                int count = metaData.getColumnCount();
                Map<String, Object> rowMap = Maps.newHashMap();
                for (int i = 0; i < count; i++) {
                    int columnIndex = i + 1;
                    String name = metaData.getColumnName(columnIndex);
                    Object value = rs.getObject(name);
                    rowMap.put(name, value);
                }
                dataList.add(rowMap);
            }
            return dataList;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    public List<Map<String, Object>> selectVipStorage() throws SQLException {
        return executeQuery(QUERY_ONE_VIP_STORAGE);
    }

    public List<Map<String, Object>> selectMasterDumperInfo() throws SQLException {
        return executeQuery(QUERY_DUMPER_MASTER_INFO);
    }

    public List<Map<String, Object>> selectLatestSnapshotLogicMetaHistory(String rollbackTSO) throws SQLException {
        if (StringUtils.isNotBlank(rollbackTSO)) {
            return executeQuery(String.format(QUERY_BASE_SNAPSHOT_WITH_TSO, rollbackTSO));
        } else {
            return executeQuery(QUERY_BASE_SNAPSHOT);
        }
    }

    public List<Map<String, Object>> selectLogicMetaDDLList(String snapTso, String rollbackTSO, int pageSize)
        throws SQLException {
        return executeQuery(String.format(QUERY_HISTORY_DDL_BY_PAGE, snapTso, rollbackTSO, pageSize));
    }

    public MySqlInfo providerMySqlInfo() {
        MySqlInfo mySqlInfo = new MySqlInfo();
        Connection conn = provider.getConnection();
        try {
            mySqlInfo.init(new MysqlConnection(conn));
            return mySqlInfo;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ue) {

                }
            }
        }

    }
}
