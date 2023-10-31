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
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.aliyun.polardbx.binlog.canal.DefaultBinlogFileInfoFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.DirectLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.MySQLConnectionException;
import com.aliyun.polardbx.binlog.canal.exception.SQLExecuteException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * mysql链接相关处理
 *
 * @author agapple 2017年7月19日 下午2:59:41
 * @since 3.2.4
 */
public class MysqlConnection implements ErosaConnection {

    private static Logger logger = LoggerFactory.getLogger(MysqlConnection.class);
    /**
     * 5秒超时
     */
    protected int connTimeout = 5 * 1000;
    /**
     * 1小时
     */
    protected int soTimeout = 60 * 60 * 1000;
    /**
     * 16k
     */
    protected int bufferSize = 16 * 1024;
    private AuthenticationInfo authInfo;
    private Connection conn;
    private BinlogFormat binlogFormat;
    private BinlogImage binlogImage;
    private ServerCharactorSet serverCharactorSet;
    private int lowerCaseTableNames;
    private int binlogChecksum = LogEvent.BINLOG_CHECKSUM_ALG_OFF;
    private String sqlMode;

    public MysqlConnection(AuthenticationInfo authInfo) {
        this.authInfo = authInfo;
    }

    public MysqlConnection(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void connect() throws IOException {
        Properties info = new Properties();
        info.put("user", authInfo.getUsername());
        info.put("password", authInfo.getPassword());
        info.put("connectTimeout", String.valueOf(connTimeout));
        info.put("socketTimeout", String.valueOf(soTimeout));
        String url = "jdbc:mysql://" + authInfo.getAddress().getHostName() + ":"
            + authInfo.getAddress().getPort() + "?allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false";
        try {
            com.mysql.jdbc.Driver driver = new com.mysql.jdbc.Driver();
            conn = driver.connect(url, info);
        } catch (SQLException e) {
            throw new MySQLConnectionException(e);
        }
    }

    @Override
    public void reconnect() throws IOException {
        disconnect();
        connect();
    }

    @Override
    public void disconnect() throws IOException {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new MySQLConnectionException(e);
            }

            conn = null;
        }
    }

    /**
     * 加速主备切换时的查找速度，做一些特殊优化，比如只解析事务头或者尾
     */
    @Override
    public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {
        loadBinlogChecksum();
        reconnect();
        updateSettings();
        try (DirectLogFetcher fetcher = new DirectLogFetcher(bufferSize)) {
            fetcher.open(conn, binlogfilename, binlogPosition, (int) generateUniqueServerId());
            LogDecoder decoder = new LogDecoder();
            decoder.setNeedFixRotate(false);
            decoder.setBinlogFileSizeFetcher(new DefaultBinlogFileInfoFetcher(this));
            decoder.handle(LogEvent.ROTATE_EVENT);
            decoder.handle(LogEvent.FORMAT_DESCRIPTION_EVENT);
            decoder.handle(LogEvent.QUERY_EVENT);
            decoder.handle(LogEvent.XID_EVENT);
            decoder.handle(LogEvent.SEQUENCE_EVENT);
            decoder.handle(LogEvent.GCN_EVENT);
            decoder.handle(LogEvent.XA_PREPARE_LOG_EVENT);
            decoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
            decoder.handle(LogEvent.WRITE_ROWS_EVENT);
            decoder.handle(LogEvent.UPDATE_ROWS_EVENT_V1);
            decoder.handle(LogEvent.UPDATE_ROWS_EVENT);
            decoder.handle(LogEvent.DELETE_ROWS_EVENT_V1);
            decoder.handle(LogEvent.DELETE_ROWS_EVENT);
            decoder.handle(LogEvent.TABLE_MAP_EVENT);
            decoder.handle(LogEvent.ROWS_QUERY_LOG_EVENT);
            LogContext context = new LogContext();
            context.setServerCharactorSet(getDefaultDatabaseCharset());
            context.setFormatDescription(new FormatDescriptionLogEvent(4, binlogChecksum));
            LogPosition logPosition = new LogPosition(binlogfilename, binlogPosition);
            context.setLogPosition(logPosition);
            while (fetcher.fetch()) {
                LogEvent logEvent = decoder.decode(fetcher, context);

                if (Thread.interrupted()) {
                    break;
                }

                if (logEvent == null) {
                    continue;
                }

                if (!func.sink(logEvent, context.getLogPosition())) {
                    break;
                }
            }
        }
    }

    @Override
    public void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills,
                     SinkFunction func) throws Exception {
        loadBinlogChecksum();
        getDefaultDatabaseCharset();
        reconnect();
        updateSettings();
        try (DirectLogFetcher fetcher = new DirectLogFetcher(bufferSize)) {
            fetcher.open(conn, binlogfilename, binlogPosition, (int) generateUniqueServerId());
            LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
            decoder.setNeedFixRotate(false);
            decoder.setBinlogFileSizeFetcher(new DefaultBinlogFileInfoFetcher(this));
            LogContext context = new LogContext();
            context.setFormatDescription(new FormatDescriptionLogEvent(4, binlogChecksum));
            context.setServerCharactorSet(getDefaultDatabaseCharset());

            while (fetcher.fetch()) {
                LogEvent event = decoder.decode(fetcher, context);

                if (event == null) {
                    continue;
                }

                if (!func.sink(event, context.getLogPosition())) {
                    break;
                }
            }
        }
    }

    @Override
    public void dump(long timestamp, SinkFunction func) throws IOException {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public void dump(GTIDSet gtidSet, SinkFunction func) throws IOException {
        throw new NullPointerException("Not implement yet");
    }

    @Override
    public MysqlConnection fork() {
        MysqlConnection connection = new MysqlConnection(authInfo);
        return connection;
    }

    @Override
    public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search) throws IOException {
        updateSettings();
        DirectLogFetcher fetcher = new DirectLogFetcher(bufferSize);
        fetcher.open(conn, binlogfilename, binlogPosition, (int) generateUniqueServerId());
        return fetcher;
    }

    @Override
    public BinlogPosition findEndPosition(Long tso) {
        return query("show master status", new MysqlConnection.ProcessJdbcResult<BinlogPosition>() {

            @Override
            public BinlogPosition process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    String fileName = rs.getString(1);
                    String position = rs.getString(2);
                    String str = fileName + ':' + position + "#-2.0";
                    return BinlogPosition.parseFromString(str);
                } else {
                    throw new CanalParseException(
                        "command : 'show master status' has an error! pls check. you need (at least one of) the "
                            + "SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    public int update(String sql) {
        synchronized (this) {
            Statement stmt = null;
            try {
                stmt = conn.createStatement();
                return stmt.executeUpdate(sql);
            } catch (SQLException e) {
                logger.error("SQLException: original sql: {}, actual statement:{}", sql, stmt);
                throw new SQLExecuteException(e);
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
    }

    // ====================== help method ====================

    /**
     * the settings that will need to be checked or set:<br>
     * <ol>
     * <li>wait_timeout</li>
     * <li>net_write_timeout</li>
     * <li>net_read_timeout</li>
     * </ol>
     */
    private void updateSettings() throws IOException {
        try {
            update("set wait_timeout=9999999");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }
        try {
            update("set net_write_timeout=1800");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }

        try {
            update("set net_read_timeout=1800");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }

        try {
            // 设置服务端返回结果时不做编码转化，直接按照数据库的二进制编码进行发送，由客户端自己根据需求进行编码转化
            update("set names 'binary'");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }

        try {
            // mysql5.6针对checksum支持需要设置session变量
            // 如果不设置会出现错误： Slave can not handle replication events with the
            // checksum that master is configured to log
            // 但也不能乱设置，需要和mysql server的checksum配置一致，不然RotateLogEvent会出现乱码
            update("set @master_binlog_checksum= @@global.binlog_checksum");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }

        try {
            // 参考:https://github.com/alibaba/canal/issues/284
            // mysql5.6需要设置slave_uuid避免被server kill链接
            update("set @slave_uuid=uuid()");
        } catch (Exception e) {
            if (!StringUtils.contains(e.getMessage(), "Unknown system variable")) {
                logger.warn(ExceptionUtils.getStackTrace(e));
            }
        }

        try {
            // mariadb针对特殊的类型，需要设置session变量
            update("SET @mariadb_slave_capability='" + LogEvent.MARIA_SLAVE_CAPABILITY_MINE + "'");
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }
    }

    public int loadBinlogChecksum() {
        query("select @@global.binlog_checksum", new ProcessJdbcResult<String>() {

            @Override
            public String process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    String binlogChecksumOption = rs.getString(1);
                    if (logger.isDebugEnabled()) {
                        logger.debug("binlog checksum option : " + binlogChecksumOption);
                    }
                    if (StringUtils.equalsIgnoreCase(binlogChecksumOption, "CRC32")) {
                        binlogChecksum = LogEvent.BINLOG_CHECKSUM_ALG_CRC32;
                    } else {
                        binlogChecksum = LogEvent.BINLOG_CHECKSUM_ALG_OFF;
                    }
                }
                return null;
            }
        });
        return binlogChecksum;
    }

    public String loadSqlMode() {
        query("select @@global.sql_mode", new ProcessJdbcResult<String>() {
            @Override
            public String process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    sqlMode = rs.getString(1);
                    if (logger.isDebugEnabled()) {
                        logger.debug("default sql mode : " + sqlMode);
                    }
                }
                return sqlMode;
            }
        });
        return sqlMode;
    }

    private void loadServerDatabaseCharset() {
        query("show variables like '%character%'", new ProcessJdbcResult<String>() {

            @Override
            public String process(ResultSet rs) throws SQLException {
                ServerCharactorSet set = new ServerCharactorSet();
                while (rs.next()) {
                    String variableName = rs.getString(1);
                    String charset = rs.getString(2);
                    ServerCharactorSet.commonSet(set, variableName, charset);
                }
                serverCharactorSet = set;
                return null;
            }
        });
    }

    private void loadLowerCaseTableNames() {
        lowerCaseTableNames = query("show variables like 'lower_case_table_names'", new ProcessJdbcResult<Integer>() {

            @Override
            public Integer process(ResultSet rs) throws SQLException {
                Integer lowerCaseValue = 0;
                if (rs.next()) {
                    lowerCaseValue = rs.getInt(2);
                }
                return lowerCaseValue;
            }
        });
    }

    /**
     * 获取一下binlog format格式
     */
    private void loadBinlogFormat() {
        binlogFormat = query("show variables like 'binlog_format'", new ProcessJdbcResult<BinlogFormat>() {

            @Override
            public BinlogFormat process(ResultSet rs) throws SQLException {
                BinlogFormat binlogFormat = null;
                if (rs.next()) {
                    String value = rs.getString(2);
                    binlogFormat = BinlogFormat.valuesOf(value);
                    if (binlogFormat == null) {
                        throw new IllegalStateException("unexpected binlog format query result:" + value);
                    }
                }
                return binlogFormat;
            }
        });
    }

    /**
     * 获取一下binlog image格式
     */
    private void loadBinlogImage() {
        binlogImage = query("show variables like 'binlog_row_image'", new ProcessJdbcResult<BinlogImage>() {

            @Override
            public BinlogImage process(ResultSet rs) throws SQLException {
                BinlogImage binlogImage = null;
                String value = null;
                if (rs.next()) {
                    value = rs.getString(2);
                    binlogImage = BinlogImage.valuesOf(value);
                } else {
                    // 没数据
                    binlogImage = BinlogImage.FULL;
                }

                if (binlogImage == null) {
                    throw new IllegalStateException("unexpected binlog format query result:" + value);
                }
                return binlogImage;
            }
        });
    }

    public <T> T query(String sql, ProcessJdbcResult<T> processor) {
        synchronized (this) {
            Statement stmt = null;
            ResultSet rs = null;
            try {
                stmt = conn.createStatement();
                rs = stmt.executeQuery(sql);
                return processor.process(rs);
            } catch (SQLException e) {
                logger.error("SQLException: original sql: {}, actual statement:{}, exception: {}", sql, stmt, e);
                throw new SQLExecuteException(e);
            } finally {
                try {
                    if (rs != null) {
                        rs.close();
                    }
                    if (stmt != null) {
                        stmt.close();
                    }
                } catch (SQLException e) {
                    // ignore
                }

            }
        }
    }

    @Override
    public long binlogFileSize(String searchFileName) throws IOException {
        if (StringUtils.isBlank(searchFileName)) {
            return -1;
        }
        return query("show binary logs", new MysqlConnection.ProcessJdbcResult<Long>() {

            @Override
            public Long process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    String fileName = rs.getString(1);
                    if (fileName.equalsIgnoreCase(searchFileName)) {
                        return rs.getLong(2);
                    }
                }
                return -1L;
            }
        });
    }

    @Override
    public String preFileName(String currentFileName) {
        // 继续往前找
        int split = currentFileName.indexOf(".");
        int binlogSeqNum = Integer
            .parseInt(currentFileName.substring(split + 1));
        binlogSeqNum--;
        if (binlogSeqNum < 1) {
            return null;
        }
        currentFileName = currentFileName.substring(0, split) + "." + StringUtils
            .leftPad(binlogSeqNum + "", currentFileName.length() - split - 1, "0");
        return currentFileName;
    }

    /**
     * Generate an unique server-id for binlog dump.
     */
    private final long generateUniqueServerId() {
        try {
            // a=`echo $masterip|cut -d\. -f1`
            // b=`echo $masterip|cut -d\. -f2`
            // c=`echo $masterip|cut -d\. -f3`
            // d=`echo $masterip|cut -d\. -f4`
            // #server_id=`expr $a \* 256 \* 256 \* 256 + $b \* 256 \* 256 + $c \* 256 + $d `
            // #server_id=$b$c$d
            // server_id=`expr $b \* 256 \* 256 + $c \* 256 + $d `
            InetAddress localHost = InetAddress.getLocalHost();
            byte[] addr = localHost.getAddress();
            int salt = 0;
            return ((0x7f & salt) << 24) + ((0xff & (int) addr[1]) << 16) // NL
                + ((0xff & (int) addr[2]) << 8) // NL
                + (0xff & (int) addr[3]);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unknown host", e);
        }
    }

    public BinlogFormat getBinlogFormat() {
        if (binlogFormat == null) {
            loadBinlogFormat();
        }

        return binlogFormat;
    }

    public BinlogImage getBinlogImage() {
        if (binlogImage == null) {
            loadBinlogImage();
        }

        return binlogImage;
    }

    public int getLowerCaseTableNames() {
        loadLowerCaseTableNames();
        return lowerCaseTableNames;
    }

    public ServerCharactorSet getDefaultDatabaseCharset() {
        if (serverCharactorSet == null) {
            loadServerDatabaseCharset();
        }

        return serverCharactorSet;
    }

    public void setServerCharactorset(ServerCharactorSet serverCharactorSet) {
        this.serverCharactorSet = serverCharactorSet;
    }

    // ================== setter / getter ===================

    public Connection getConn() {
        return conn;
    }

    public InetSocketAddress getAddress() {
        return authInfo.getAddress();
    }

    public void setConnTimeout(int connTimeout) {
        this.connTimeout = connTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public static enum BinlogFormat {

        STATEMENT("STATEMENT"), ROW("ROW"), MIXED("MIXED");

        private String value;

        private BinlogFormat(String value) {
            this.value = value;
        }

        public static BinlogFormat valuesOf(String value) {
            BinlogFormat[] formats = values();
            for (BinlogFormat format : formats) {
                if (format.value.equalsIgnoreCase(value)) {
                    return format;
                }
            }
            return null;
        }

        public boolean isStatement() {
            return this == STATEMENT;
        }

        public boolean isRow() {
            return this == ROW;
        }

        public boolean isMixed() {
            return this == MIXED;
        }
    }

    /**
     * http://dev.mysql.com/doc/refman/5.6/en/replication-options-binary-log.
     * html#sysvar_binlog_row_image
     *
     * @author agapple 2015年6月29日 下午10:39:03
     * @since 1.0.20
     */
    public static enum BinlogImage {

        FULL("FULL"), MINIMAL("MINIMAL"), NOBLOB("NOBLOB");

        private String value;

        private BinlogImage(String value) {
            this.value = value;
        }

        public static BinlogImage valuesOf(String value) {
            BinlogImage[] formats = values();
            for (BinlogImage format : formats) {
                if (format.value.equalsIgnoreCase(value)) {
                    return format;
                }
            }
            return null;
        }

        public boolean isFull() {
            return this == FULL;
        }

        public boolean isMinimal() {
            return this == MINIMAL;
        }

        public boolean isNoBlob() {
            return this == NOBLOB;
        }
    }

    public static interface ProcessJdbcResult<T> {

        public T process(ResultSet rs) throws SQLException;
    }
}
