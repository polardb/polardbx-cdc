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

import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.exception.MySQLConnectionException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author agapple 2017年7月19日 下午4:22:55
 * @since 3.2.5
 */
public class MysqlConnector {

    private Connection conn;
    private AuthenticationInfo authInfo;
    private int connTimeout = 5 * 1000;
    private int soTimeout = 30 * 1000;

    public MysqlConnector(AuthenticationInfo authInfo) {
        this.authInfo = authInfo;
    }

    public void connect() throws IOException {
        Properties info = new Properties();
        info.put("user", authInfo.getUsername());
        info.put("password", authInfo.getPassword());
        info.put("connectTimeout", String.valueOf(connTimeout));
        info.put("socketTimeout", String.valueOf(soTimeout));
        String url = "jdbc:mysql://" + authInfo.getAddress().getHostName() + "/"
            + String.valueOf(authInfo.getAddress().getPort());
        try {
            conn = DriverManager.getConnection(url, info);
        } catch (SQLException e) {
            throw new MySQLConnectionException(e);
        }
    }

    public void reconnect() throws IOException {
        disconnect();
        connect();
    }

    public void disconnect() throws IOException {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new MySQLConnectionException(e);
            }
        }
    }

    public MysqlConnector fork() {
        MysqlConnector connector = new MysqlConnector(authInfo);
        connector.setConnTimeout(connTimeout);
        connector.setSoTimeout(getSoTimeout());
        return connector;
    }

    public AuthenticationInfo getAuthInfo() {
        return authInfo;
    }

    public int getConnTimeout() {
        return connTimeout;
    }

    public void setConnTimeout(int connTimeout) {
        this.connTimeout = connTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Connection getConn() {
        return conn;
    }

}
