/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import lombok.Getter;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.getSqlMode;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.dnCount;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getConnectionProperties;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getMetaDB;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.isReplicaTest;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.useDruid;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * 初始化所有连接
 */
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private static final int MAX_ACTIVE = 60;
    private static final ConnectionManager connectionManager = new ConnectionManager();
    private Properties configProp;
    private boolean inited = false;

    private boolean skipInitDataNode = false;
    private String dnMysqlUser;
    private String dnMysqlPassword;
    @Getter
    private String dnMysqlPort;
    @Getter
    private String dnMysqlAddress;

    private String dnMysqlUserSecond;
    private String dnMysqlPasswordSecond;
    @Getter
    private String dnMysqlPortSecond;
    @Getter
    private String dnMysqlAddressSecond;

    @Getter
    private String polardbxUser;
    @Getter
    private String polardbxPassword;
    @Getter
    private String polardbxPort;
    @Getter
    private String polardbxAddress;

    @Getter
    private String metaUser;
    @Getter
    private String metaPassword;
    @Getter
    private String metaPort;
    @Getter
    private String metaAddress;

    @Getter
    private String cdcSyncDbUser;
    @Getter
    private String cdcSyncDbPassword;
    @Getter
    private String cdcSyncDbPort;
    @Getter
    private String cdcSyncDbAddress;

    private String cdcSyncDbUserFirst;
    private String cdcSyncDbPasswordFirst;
    private String cdcSyncDbPortFirst;
    private String cdcSyncDbAddressFirst;

    @Getter
    private String cdcSyncDbUserSecond;
    @Getter
    private String cdcSyncDbPasswordSecond;
    @Getter
    private String cdcSyncDbPortSecond;
    @Getter
    private String cdcSyncDbAddressSecond;

    @Getter
    private String cdcSyncDbUserThird;
    @Getter
    private String cdcSyncDbPasswordThird;
    @Getter
    private String cdcSyncDbPortThird;
    @Getter
    private String cdcSyncDbAddressThird;

    @Getter
    private DruidDataSource dnDataSource;
    @Getter
    private DruidDataSource dnDataSourceSecond;
    @Getter
    private DruidDataSource metaDataSource;
    @Getter
    private DruidDataSource polardbxDataSource;
    @Getter
    private DruidDataSource cdcSyncDbDataSource;
    @Getter
    private DruidDataSource cdcSyncDbDataSourceFirst;
    @Getter
    private DruidDataSource cdcSyncDbDataSourceSecond;
    @Getter
    private DruidDataSource cdcSyncDbDataSourceThird;

    @Getter
    private boolean enableOpenSSL;
    @Getter
    private String polardbxMode;
    @Getter
    private String mysqlMode;

    public static ConnectionManager getInstance() {
        if (!connectionManager.isInited()) {
            synchronized (connectionManager) {
                if (!connectionManager.isInited()) {
                    connectionManager.init();
                }
            }
        }
        return connectionManager;
    }

    public static DruidDataSource getDruidDataSource(String server, String port, String user, String password,
                                                     String db, boolean isMysql) {
        String connProp = getConnectionProperties(isMysql);
        String url = String.format(ConfigConstant.URL_PATTERN_WITH_DB + connProp, server, port, db);
        return getDruidDataSourceInternal(url, user, password);
    }

    public static DruidDataSource getDruidDataSourceWithoutDB(String server, String port, String user,
                                                              String password, boolean isMysql) {
        String connProp = getConnectionProperties(isMysql);
        String url = String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + connProp, server, port);
        return getDruidDataSourceInternal(url, user, password);
    }

    private static DruidDataSource getDruidDataSourceInternal(String url, String user, String password) {
        DruidDataSource druidDs = new DruidDataSource();
        druidDs.setUrl(url);
        druidDs.setUsername(user);
        druidDs.setPassword(password);
        druidDs.setRemoveAbandoned(false);
        druidDs.setMaxActive(MAX_ACTIVE);
        try {
            druidDs.init();
            druidDs.getConnection();
        } catch (SQLException e) {
            String errorMs = "[DruidDataSource getConnection] failed! ";
            log.error(errorMs, e);
            Assert.fail(errorMs);
        }
        return druidDs;
    }

    private boolean isInited() {
        return inited;
    }

    private void init() {
        //jdk 放开tls限制
        //Security.setProperty(PROPERTY_TLS_DISABLED_ALGS, "");

        this.configProp = PropertiesUtil.configProp;

        this.skipInitDataNode = Boolean.parseBoolean(configProp.getProperty(ConfigConstant.SKIP_INIT_MYSQL));
        this.dnMysqlUser = configProp.getProperty(ConfigConstant.DN_MYSQL_USER);
        this.dnMysqlPassword = configProp.getProperty(ConfigConstant.DN_MYSQL_PASSWORD);
        this.dnMysqlPort = configProp.getProperty(ConfigConstant.DN_MYSQL_PORT);
        this.dnMysqlAddress = configProp.getProperty(ConfigConstant.DN_MYSQL_ADDRESS);

        this.dnMysqlUserSecond = configProp.getProperty(ConfigConstant.DN_MYSQL_USER_SECOND);
        this.dnMysqlPasswordSecond = configProp.getProperty(ConfigConstant.DN_MYSQL_PASSWORD_SECOND);
        this.dnMysqlPortSecond = configProp.getProperty(ConfigConstant.DN_MYSQL_PORT_SECOND);
        this.dnMysqlAddressSecond = configProp.getProperty(ConfigConstant.DN_MYSQL_ADDRESS_SECOND);

        this.polardbxUser = configProp.getProperty(ConfigConstant.POLARDBX_USER);
        this.polardbxPassword = configProp.getProperty(ConfigConstant.POLARDBX_PASSWORD);
        this.polardbxPort = configProp.getProperty(ConfigConstant.POLARDBX_PORT);
        this.polardbxAddress = configProp.getProperty(ConfigConstant.POLARDBX_ADDRESS);

        this.metaUser = configProp.getProperty(ConfigConstant.META_USER);
        this.metaPassword = PasswdUtil.decrypt(configProp.getProperty(ConfigConstant.META_PASSWORD));
        this.metaPort = configProp.getProperty(ConfigConstant.META_PORT);
        this.metaAddress = configProp.getProperty(ConfigConstant.META_ADDRESS);

        this.cdcSyncDbUser = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_USER);
        this.cdcSyncDbPassword = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PASSWORD);
        this.cdcSyncDbPort = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PORT);
        this.cdcSyncDbAddress = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_ADDRESS);

        this.cdcSyncDbUserFirst = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_USER_FIRST);
        this.cdcSyncDbPasswordFirst = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PASSWORD_FIRST);
        this.cdcSyncDbPortFirst = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PORT_FIRST);
        this.cdcSyncDbAddressFirst = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_ADDRESS_FIRST);

        this.cdcSyncDbUserSecond = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_USER_SECOND);
        this.cdcSyncDbPasswordSecond = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PASSWORD_SECOND);
        this.cdcSyncDbPortSecond = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PORT_SECOND);
        this.cdcSyncDbAddressSecond = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_ADDRESS_SECOND);

        this.cdcSyncDbUserThird = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_USER_THIRD);
        this.cdcSyncDbPasswordThird = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PASSWORD_THIRD);
        this.cdcSyncDbPortThird = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_PORT_THIRD);
        this.cdcSyncDbAddressThird = configProp.getProperty(ConfigConstant.CDC_SYNC_DB_ADDRESS_THIRD);

        try {
            if (!skipInitDataNode) {
                this.dnDataSource = getDruidDataSource(dnMysqlAddress, dnMysqlPort, dnMysqlUser, dnMysqlPassword,
                    PropertiesUtil.mysqlDBName1(), true);
                setMysqlParameter(dnDataSource);

                if (dnCount > 1) {
                    this.dnDataSourceSecond =
                        getDruidDataSource(dnMysqlAddressSecond, dnMysqlPortSecond, dnMysqlUserSecond,
                            dnMysqlPasswordSecond, PropertiesUtil.mysqlDBName1(), true);
                    setMysqlParameter(dnDataSourceSecond);
                }
                try (Connection mysqlCon = dnDataSource.getConnection()) {
                    this.enableOpenSSL = checkSupportOpenSSL(mysqlCon);
                    this.mysqlMode = getSqlMode(mysqlCon);
                }
            }

            this.metaDataSource = getDruidDataSource(metaAddress, metaPort, metaUser, metaPassword, getMetaDB, true);

            this.polardbxDataSource = getDruidDataSource(polardbxAddress, polardbxPort, polardbxUser, polardbxPassword,
                PropertiesUtil.polardbXDBName1(false), false);

            if (usingBinlogX) {
                this.cdcSyncDbDataSourceFirst =
                    getDruidDataSourceWithoutDB(cdcSyncDbAddressFirst, cdcSyncDbPortFirst, cdcSyncDbUserFirst,
                        cdcSyncDbPasswordFirst, true);
                this.cdcSyncDbDataSourceSecond =
                    getDruidDataSourceWithoutDB(cdcSyncDbAddressSecond, cdcSyncDbPortSecond, cdcSyncDbUserSecond,
                        cdcSyncDbPasswordSecond, true);
                this.cdcSyncDbDataSourceThird =
                    getDruidDataSourceWithoutDB(cdcSyncDbAddressThird, cdcSyncDbPortThird, cdcSyncDbUserThird,
                        cdcSyncDbPasswordThird, true);
            } else {
                // 去除附加的"mysql"，以兼容polardb-x作为测试下游
                this.cdcSyncDbDataSource =
                    getDruidDataSourceWithoutDB(cdcSyncDbAddress, cdcSyncDbPort, cdcSyncDbUser, cdcSyncDbPassword,
                        !isReplicaTest());
            }

            try (Connection polardbxCon = polardbxDataSource.getConnection()) {
                this.polardbxMode = getSqlMode(polardbxCon);
            }
        } catch (Throwable t) {
            log.error(this.toString(), t);
            throw new RuntimeException(t);
        }

        inited = true;
    }

    private void setMysqlParameter(DataSource dataSource) {
        try (Connection mysqlConnection = dataSource.getConnection()) {
            JdbcUtil.executeUpdate(mysqlConnection, "set global innodb_buffer_pool_size=6442450944;");
            JdbcUtil.executeUpdate(mysqlConnection, "set global table_open_cache=20000;");
            JdbcUtil.executeUpdate(mysqlConnection, "set global table_definition_cache=20000;");
            JdbcUtil.executeUpdate(mysqlConnection, "set global sync_binlog=1000;");
            JdbcUtil.executeUpdate(mysqlConnection, "set global innodb_flush_log_at_trx_commit=2;");
        } catch (Throwable t) {
            //ignore
        }
    }

    public Connection getDruidDataNodeConnection() throws SQLException {
        if (useDruid) {
            return getDnDataSource().getConnection();
        } else {
            return newMysqlConnection();
        }
    }

    public Connection getDruidDataNodeConnectionSecond() throws SQLException {
        if (useDruid) {
            return getDnDataSourceSecond().getConnection();
        } else {
            return newMysqlConnectionSecond();
        }
    }

    public Connection getDruidMetaConnection() throws SQLException {
        if (useDruid) {
            return getMetaDataSource().getConnection();
        } else {
            String connProp = getConnectionProperties(true);
            String url = String.format(ConfigConstant.URL_PATTERN_WITH_DB + connProp, metaAddress, metaPort, getMetaDB);
            return JdbcUtil.createConnection(url, metaUser, metaPassword);
        }
    }

    public Connection getDruidPolardbxConnection() throws SQLException {
        if (useDruid) {
            return getPolardbxDataSource().getConnection();
        } else {
            return newPolarDBXConnection();
        }
    }

    public MysqlConnection getPolarxConnectionOfMysql() {
        AuthenticationInfo auth =
            new AuthenticationInfo(new InetSocketAddress(polardbxAddress, Integer.parseInt(polardbxPort)), polardbxUser,
                polardbxPassword);
        return new MysqlConnection(auth);
    }

    public Connection getDruidCdcSyncDbConnection() throws SQLException {
        if (useDruid) {
            return getCdcSyncDbDataSource().getConnection();
        } else {
            return newCdcSyncDbConnection();
        }
    }

    public Connection getDruidCdcSyncDbConnectionFirst() throws SQLException {
        if (useDruid) {
            return getCdcSyncDbDataSourceFirst().getConnection();
        } else {
            return newCdcSyncDbConnectionFirst();
        }
    }

    public Connection getDruidCdcSyncDbConnectionSecond() throws SQLException {
        if (useDruid) {
            return getCdcSyncDbDataSourceSecond().getConnection();
        } else {
            return newCdcSyncDbConnectionSecond();
        }
    }

    public Connection getDruidCdcSyncDbConnectionThird() throws SQLException {
        if (useDruid) {
            return getCdcSyncDbDataSourceThird().getConnection();
        } else {
            return newCdcSyncDbConnectionThird();
        }
    }

    public Connection newPolarDBXConnection() {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(false), polardbxAddress,
                polardbxPort);
        return JdbcUtil.createConnection(url, polardbxUser, polardbxPassword);
    }

    public Connection newPolarDBXConnectionWithExtraParams(String extraParams) {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(false), polardbxAddress,
                polardbxPort);
        url += extraParams;
        return JdbcUtil.createConnection(url, polardbxUser, polardbxPassword);
    }

    /**
     * get connection from a specific user
     */
    public Connection newPolarDBXConnection(String user, String password) {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(false), polardbxAddress,
                polardbxPort);
        return JdbcUtil.createConnection(url, user, password);
    }

    public Connection newMysqlConnection() {
        String connProp = getConnectionProperties(true);
        String url = String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + connProp, dnMysqlAddress, dnMysqlPort);
        return JdbcUtil.createConnection(url, dnMysqlUser, dnMysqlPassword);
    }

    public Connection newMysqlConnectionWithExtraParams(String extraParams) {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(true), dnMysqlAddress,
                dnMysqlPort);
        url += extraParams;
        return JdbcUtil.createConnection(url, dnMysqlUser, dnMysqlPassword);
    }

    public Connection newMysqlConnectionSecond() {
        String connProp = getConnectionProperties(true);
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + connProp, dnMysqlAddressSecond, dnMysqlPortSecond);
        return JdbcUtil.createConnection(url, dnMysqlUserSecond, dnMysqlPasswordSecond);
    }

    public Connection newCdcSyncDbConnection() {
        return newCdcSyncDbConnection(cdcSyncDbUser, cdcSyncDbPassword);
    }

    public Connection newCdcSyncDbConnection(String user, String password) {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(!isReplicaTest()),
                cdcSyncDbAddress,
                cdcSyncDbPort);
        return JdbcUtil.createConnection(url, user, password);
    }

    public Connection newCdcSyncDbConnectionFirst() {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(true), cdcSyncDbAddressFirst,
                cdcSyncDbPortFirst);
        return JdbcUtil.createConnection(url, cdcSyncDbUserFirst, cdcSyncDbPasswordFirst);
    }

    public Connection newCdcSyncDbConnectionSecond() {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(true), cdcSyncDbAddressSecond,
                cdcSyncDbPortSecond);
        return JdbcUtil.createConnection(url, cdcSyncDbUserSecond, cdcSyncDbPasswordSecond);
    }

    public Connection newCdcSyncDbConnectionThird() {
        String url =
            String.format(ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(true), cdcSyncDbAddressThird,
                cdcSyncDbPortThird);
        return JdbcUtil.createConnection(url, cdcSyncDbUserThird, cdcSyncDbPasswordThird);
    }

    public void close() {
        this.dnDataSource.close();
        if (this.dnDataSourceSecond != null) {
            this.dnDataSourceSecond.close();
        }
        this.metaDataSource.close();
        this.polardbxDataSource.close();
    }

    @Override
    public String toString() {
        return "ConnectionManager{" + "configProp=" + configProp + ", inited=" + inited + ", skipInitDataNode="
            + skipInitDataNode + ", dnMysqlUser='" + dnMysqlUser + '\'' + ", dnMysqlPassword='" + dnMysqlPassword + '\''
            + ", dnMysqlPort='" + dnMysqlPort + '\'' + ", dnMysqlAddress='" + dnMysqlAddress + '\''
            + ", dnMysqlUserSecond='" + dnMysqlUserSecond + '\'' + ", dnMysqlPasswordSecond='" + dnMysqlPasswordSecond
            + '\'' + ", dnMysqlPortSecond='" + dnMysqlPortSecond + '\'' + ", dnMysqlAddressSecond='"
            + dnMysqlAddressSecond + '\'' + ", polardbxUser='" + polardbxUser + '\'' + ", polardbxPassword='"
            + polardbxPassword + '\'' + ", polardbxPort='" + polardbxPort + '\'' + ", polardbxAddress='"
            + polardbxAddress + '\'' + ", metaUser='" + metaUser + '\'' + ", metaPassword='" + metaPassword + '\''
            + ", metaPort='" + metaPort + '\'' + ", metaAddress='" + metaAddress + '\'' + ", cdcSyncDbUser='"
            + cdcSyncDbUser + '\'' + ", cdcSyncDbPassword='" + cdcSyncDbPassword + '\'' + ", cdcSyncDbPort='"
            + cdcSyncDbPort + '\'' + ", cdcSyncDbAddress='" + cdcSyncDbAddress + '\'' + ", cdcSyncDbUserSecond='"
            + cdcSyncDbUserSecond + '\'' + ", cdcSyncDbPasswordSecond='" + cdcSyncDbPasswordSecond + '\''
            + ", cdcSyncDbPortSecond='" + cdcSyncDbPortSecond + '\'' + ", cdcSyncDbAddressSecond='"
            + cdcSyncDbAddressSecond + '\'' + ", cdcSyncDbUserThird='" + cdcSyncDbUserThird + '\''
            + ", cdcSyncDbPasswordThird='" + cdcSyncDbPasswordThird + '\'' + ", cdcSyncDbPortThird='"
            + cdcSyncDbPortThird + '\'' + ", cdcSyncDbAddressThird='" + cdcSyncDbAddressThird + '\''
            + ", enableOpenSSL=" + enableOpenSSL + ", polardbxMode='" + polardbxMode + '\'' + ", mysqlMode='"
            + mysqlMode + '\'' + '}';
    }

    public boolean checkSupportOpenSSL(Connection conn) {
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW STATUS LIKE 'Rsa_public_key'")) {
            return rs.next();
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
        return false;
    }

}
