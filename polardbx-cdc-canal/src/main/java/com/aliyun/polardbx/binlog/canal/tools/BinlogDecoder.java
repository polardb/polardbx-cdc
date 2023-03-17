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
package com.aliyun.polardbx.binlog.canal.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.aliyun.polardbx.binlog.canal.binlog.DirectLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import org.apache.commons.lang3.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class BinlogDecoder {

    private String fileName = "master-bin.000001";
    private String resultFile;
    private boolean header;
    private boolean body = true;
    private ServerCharactorSet set;

    public static void main(String[] args) throws IOException, SQLException {
        BinlogDecoder decoder = new BinlogDecoder();
        decoder.decodeRemove("11.158.129.153", "3306", "diamond", "diamond1qaz@2wsx");
    }

    public void decodeRemove(String ip, String port, String username, String pwd) throws SQLException, IOException {
        Connection conn = connect(ip, port, username, pwd);
        update(conn);
        DirectLogFetcher fetcher = new DirectLogFetcher();
        fetcher.open(conn, fileName, 987987);
        FileOutputStream fos = new FileOutputStream("/Users/yanfenglin/Downloads/tmp/bin01.txt");
        PrintStream printStream = new PrintStream(fos);
        decode(fetcher, printStream);
        fetcher.close();
        printStream.close();
    }

    public void update(Connection connection) throws SQLException {
        Statement st = connection.createStatement();
        st.executeUpdate("set wait_timeout=9999999");
        st.executeUpdate("set net_write_timeout=1800");
        st.executeUpdate("set net_read_timeout=1800");
        st.executeUpdate("set @master_binlog_checksum= '@@global.binlog_checksum'");
        st.executeUpdate("set @slave_uuid=uuid()");
        st.executeUpdate("SET @mariadb_slave_capability='" + LogEvent.MARIA_SLAVE_CAPABILITY_MINE + "'");
        ResultSet rs = st.executeQuery("show variables like '%character%'");
        set = new ServerCharactorSet();
        while (rs.next()) {
            String variableName = rs.getString(1);
            String charset = rs.getString(2);
            if ("utf8mb3".equalsIgnoreCase(charset)) {
                charset = "utf8";
            }
            if ("character_set_client".equalsIgnoreCase(variableName)) {
                set.setCharacterSetClient(charset);
            } else if ("character_set_connection".equalsIgnoreCase(variableName)) {
                set.setCharacterSetConnection(charset);
            } else if ("character_set_database".equalsIgnoreCase(variableName)) {
                set.setCharacterSetDatabase(charset);
            } else if ("character_set_server".equalsIgnoreCase(variableName)) {
                set.setCharacterSetServer(charset);
            }
        }

        st.close();
    }

    public Connection connect(String ip, String port, String username, String pwd) throws SQLException {
        Properties info = new Properties();
        info.put("user", username);
        info.put("password", pwd);
        info.put("connectTimeout", 999 + "");
        info.put("socketTimeout", 999 + "");
        String url = "jdbc:mysql://" + ip + ":"
            + port + "?allowMultiQueries=true&useSSL=false";
        com.mysql.jdbc.Driver driver = new com.mysql.jdbc.Driver();
        return driver.connect(url, info);
    }

    public void decode(LogFetcher fetcher, PrintStream writer) throws IOException {
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext lc = new LogContext();
        lc.setServerCharactorSet(set);
        lc.setLogPosition(new LogPosition(fileName, 4));
        while (fetcher.fetch()) {
            LogEvent event = decoder.decode(fetcher, lc);
            if (event == null) {
                continue;
            }

            StringBuilder header = new StringBuilder();
            header.append("[").append(event.getClass().getSimpleName()).append("] ").append("Length=")
                .append(event.getEventLen()).append(" ").append("StartPos=").append(event.getLogPos())
                .append(" EndPos=").append(event.getLogPos() + event.getEventLen()).append(" ServerID=")
                .append(event.getServerId()).append(" Timestamp=").append(event.getWhen());
            writer.println(header.toString());
            if (body) {
                JSONObject object = new JSONObject();
                switch (event.getHeader().getType()) {
                case LogEvent.QUERY_EVENT:
                    QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                    object.put("thread_id", queryLogEvent.getSessionId());
                    object.put("exec_time", queryLogEvent.getExecTime());
                    object.put("error_code", queryLogEvent.getErrorCode());
                    object.put("schema", queryLogEvent.getDbName());
                    object.put("query", queryLogEvent.getQuery());
                    break;
                case LogEvent.XID_EVENT:
                    XidLogEvent xidLogEvent = (XidLogEvent) event;
                    object.put("xid", xidLogEvent.getXid());
                    break;
                case LogEvent.XA_PREPARE_LOG_EVENT:
                    XaPrepareLogEvent prepareLogEvent = (XaPrepareLogEvent) event;
                    object.put("format_id", prepareLogEvent.getFormatId());
                    object.put("gtrid", prepareLogEvent.getGtridLength());
                    object.put("bqual", prepareLogEvent.getBqualLength());
                    object.put("on_phase", prepareLogEvent.isOnePhase());
                    break;
                case LogEvent.WRITE_ROWS_EVENT:
                case LogEvent.WRITE_ROWS_EVENT_V1:
                case LogEvent.UPDATE_ROWS_EVENT:
                case LogEvent.UPDATE_ROWS_EVENT_V1:
                case LogEvent.DELETE_ROWS_EVENT:
                case LogEvent.DELETE_ROWS_EVENT_V1:
                    RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
                    TableMapLogEvent tableMap = rowsLogEvent.getTable();
                    RowsLogBuffer logBuffer = rowsLogEvent.getRowsBuf("utf8");
                    break;
                case LogEvent.TABLE_MAP_EVENT:
                    TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
                    object.put("table_id", tableMapLogEvent.getTableId());
                    object.put("schema", tableMapLogEvent.getDbName());
                    object.put("table", tableMapLogEvent.getTableName());
                    object.put("column_count", tableMapLogEvent.getColumnCnt());
                    object.put("null_bitmap", tableMapLogEvent.getNullBits());
                    JSONArray array = new JSONArray();
                    object.put("columns", array);
                    for (TableMapLogEvent.ColumnInfo ci : tableMapLogEvent.getColumnInfo()) {
                        JSONObject colObj = new JSONObject();
                        colObj.put("type", ci.type);
                        colObj.put("meta", ci.meta);
                        array.add(colObj);
                    }
                    break;
                case LogEvent.ROWS_QUERY_LOG_EVENT:
                    break;
                case LogEvent.SEQUENCE_EVENT:
                    SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
                    object.put("sequence_type", sequenceLogEvent.getSequenceType());
                    object.put("sequence_num", sequenceLogEvent.getSequenceNum());
                    break;
                case LogEvent.ROTATE_EVENT:
                    RotateLogEvent rotateLogEvent = (RotateLogEvent) event;
                    object.put("filename", rotateLogEvent.getFilename());
                    object.put("position", rotateLogEvent.getPosition());
                    break;
                }
                writer.println(JSON.toJSONString(object, SerializerFeature.PrettyFormat));
            }

            if (event.getHeader().getType() == LogEvent.ROTATE_EVENT) {
                RotateLogEvent rotateLogEvent = (RotateLogEvent) event;
                if (!StringUtils.equals(rotateLogEvent.getFilename(), fileName)) {
                    break;
                }
            }

        }
    }
}
