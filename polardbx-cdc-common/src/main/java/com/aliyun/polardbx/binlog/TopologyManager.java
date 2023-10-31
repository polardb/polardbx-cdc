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
package com.aliyun.polardbx.binlog;

import com.alibaba.druid.support.console.TableFormatter;
import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologyManager {

    private static final Logger logger = LoggerFactory.getLogger(TopologyManager.class);
    private static final String SHOW_DATASOURCE = "show datasources";
    private static final String SHOW_TABLE_TOPOLOGY = "show topology from `%s`";
    private static final String SHOW_CREATE_TABLE = "show create table `%s`";
    private static final String SHOW_TABLES = "show tables";
    protected Map<String, String> phy2logic = new HashMap();
    protected Set<String> logicTableSet = new HashSet<>();
    private final Map<String, TableTopology> tableTopologyMap = Maps.newHashMap();
    private final String ip;
    private final int port;
    private final String dbName;
    private final String username;
    private final String password;

    public TopologyManager(String ip, int port, String dbName, String username, String password) {
        this.ip = ip;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.password = password;
        this.innerParse();
    }

    private void innerParse() {
        Connection connection = null;
        try {
            logger.warn("connect to " + String.format("jdbc:mysql://%s:%s/%s", ip, port + "", dbName));
            connection = DriverManager
                .getConnection(String.format("jdbc:mysql://%s:%s/%s", ip, port + "", dbName), username, password);

            Map<String, String> dbGroup2dbNameMap = prepareDbGroup2DbName(connection);
            List<String> logicTableList = showTableList(connection);
            logger.warn("request table size :" + logicTableList.size());
            logicTableSet.addAll(logicTableList);
            for (String logicTable : logicTableList) {
                if (checkTableAvailable(connection, logicTable)) {
                    prepareTopologyForTable(connection, dbGroup2dbNameMap, logicTable);
                }
            }
            for (TableTopology tt : tableTopologyMap.values()) {
                logger.warn(tt.toString());
            }
        } catch (SQLException e) {
            throw new PolardbxException("connect to drds failed ", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private boolean checkTableAvailable(Connection connection, String table) throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(String.format(SHOW_CREATE_TABLE, table));
        if (rs.next()) {
            String ddl = rs.getString(2);
            SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, false);
            SQLStatement statement = parser.parseStatement();
            if (statement instanceof MySqlCreateTableStatement) {
                return true;
            }
        }
        logger.warn("skip parser failed table : " + table);
        return false;
    }

    private void prepareTopologyForTable(Connection connection, Map<String, String> dbGroup2dbNameMap,
                                         String table) throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(String.format(SHOW_TABLE_TOPOLOGY, table));
        while (rs.next()) {
            String groupName = rs.getString("GROUP_NAME");
            String phyTableName = rs.getString("TABLE_NAME").toLowerCase();
            String phyDbName = dbGroup2dbNameMap.get(groupName);
            phy2logic.put(phyTableName, table);
            TableTopology tt = tableTopologyMap.get(table);
            if (tt == null) {
                tt = new TableTopology(table);
                tableTopologyMap.put(table, tt);
            }
            tt.add(phyDbName, phyTableName);
        }
        rs.close();
        st.close();
    }

    private List<String> showTableList(Connection connection) throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(SHOW_TABLES);
        List<String> tableList = Lists.newArrayList();
        while (rs.next()) {
            String tb = rs.getString(1);
            tableList.add(tb.toLowerCase());
        }
        return tableList;
    }

    private Map<String, String> prepareDbGroup2DbName(Connection connection) throws SQLException {
        Map<String, String> dbGroup2dbNameMap = Maps.newHashMap();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(SHOW_DATASOURCE);
        try {
            while (rs.next()) {
                String dbGroup = rs.getString("GROUP");
                String url = rs.getString("URL");
                String beforeUrl = StringUtils.substringBeforeLast(url, "?");
                String dbName = StringUtils.substringAfterLast(beforeUrl, "/");
                dbGroup2dbNameMap.put(dbGroup, dbName.toLowerCase());
            }
        } finally {
            rs.close();
            st.close();
        }
        return dbGroup2dbNameMap;
    }

    /**
     * 从logicTableSet 集合中选取对应的物理表，并且，如果无法从规则中读取则直接提取对应表到返回结果中
     */
    public List<String> getAllPhyTableList(String phyDbName, Set<String> logicTableSet) {
        phyDbName = phyDbName.toLowerCase();
        List<String> allPhyTableList = Lists.newArrayList();
        for (String logicTable : logicTableSet) {
            TableTopology tt = tableTopologyMap.get(logicTable.toLowerCase());
            if (tt != null && tt.get(phyDbName) != null) {
                allPhyTableList.addAll(tt.get(phyDbName));
            }
        }
        return allPhyTableList;
    }

    public String getLogicTable(String phyTableName) {
        return phy2logic.get(phyTableName);
    }

    public Set<String> getLogicTableSet() {
        return logicTableSet;
    }

    public static class TableTopology {
        private Map<String, List<String>> phyDb2phyTableList = Maps.newHashMap();
        private String logicTableName;

        public TableTopology(String logicTableName) {
            this.logicTableName = logicTableName;
        }

        public void add(String phyDbName, String phyTable) {
            List<String> phyTableList = phyDb2phyTableList.get(phyDbName);
            if (phyTableList == null) {
                phyTableList = Lists.newArrayList();
                phyDb2phyTableList.put(phyDbName, phyTableList);
            }
            phyTableList.add(phyTable);
        }

        public List<String> get(String phyDbName) {
            return phyDb2phyTableList.get(phyDbName);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            List<String[]> lst = new ArrayList<>();
            lst.add(new String[] {"phyDbName", "phyTableName"});
            for (Map.Entry<String, List<String>> entry : phyDb2phyTableList.entrySet()) {
                for (String str : entry.getValue()) {
                    String[] arr = new String[2];
                    arr[0] = entry.getKey();
                    arr[1] = str;
                    lst.add(arr);
                }
            }
            Collections.sort(lst, Comparator.comparing((String[] o) -> o[1]).thenComparing(o -> o[0]));
            sb.append("\nLogicTable:").append(logicTableName).append("\n").append(TableFormatter.format(lst));
            return sb.toString();
        }
    }
}
