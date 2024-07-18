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
package com.aliyun.polardbx.rpl.common;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;
import com.alibaba.druid.pool.vendor.MySqlValidConnectionChecker;
import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author shicai.xsc 2020/12/1 22:36
 * @since 5.0.0.0
 */
@Slf4j
public class DataSourceUtil {

    public static Map<String, String> DEFAULT_MYSQL_CONNECTION_PROPERTIES = Maps.newHashMap();
    public static String SHORT_SOCKET_TIMEOUT =
        DynamicApplicationConfig.getString(ConfigKeys.RPL_SHORT_SOCKET_TIMEOUT_MILLS);
    public static String LONG_SOCKET_TIMEOUT =
        DynamicApplicationConfig.getString(ConfigKeys.RPL_LONG_SOCKET_TIMEOUT_MILLS);

    static {
        // 开启多语句能力
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowMultiQueries", "true");
        // 全量目标数据源加上这个批量的参数
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("rewriteBatchedStatements", "true");
        // 关闭每次读取read-only状态,提升batch性能
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("readOnlyPropagatesToServer", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("connectTimeout", "1000");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("autoReconnect", "true");
        // 将0000-00-00的时间类型返回null
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("zeroDateTimeBehavior", "convertToNull");
        // 直接返回字符串，不做year转换date处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("yearIsDateType", "false");
        // 返回时间类型的字符串,不做时区处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("noDatetimeStringSync", "true");
        // 不处理tinyint转为bit
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("tinyInt1isBit", "false");
        // 16MB，兼容一下ADS不支持mysql，5.1.38+的server变量查询为大写的问题，人肉指定一下最大包大小
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("maxAllowedPacket", "1073741824");
        // net_write_timeout
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("netTimeoutForStreamingResults", "72000");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("useServerPrepStmts", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("useInformationSchema", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("pedantic", "true");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowLoadLocalInfile", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowLocalInfile", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowUrlInLocalInfile", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("autoDeserialize", "false");
    }

    public static void closeQuery(ResultSet rs, Statement stmt, Connection conn) {
        JdbcUtils.closeResultSet(rs);
        JdbcUtils.closeStatement(stmt);
        JdbcUtils.closeConnection(conn);
    }

    public static DruidDataSource createDruidMySqlDataSource(boolean usePolarxPoolCN, String ip, int port,
                                                             String dbName, String user,
                                                             String passwd, String encoding, int minPoolSize,
                                                             int maxPoolSize, boolean longSql,
                                                             Map<String, String> params,
                                                             List<String> newConnectionSQLs) throws Exception {
        if (params == null) {
            params = new HashMap<>();
        }
        params.put("socketTimeout", longSql ? LONG_SOCKET_TIMEOUT : SHORT_SOCKET_TIMEOUT);
        if (usePolarxPoolCN) {
            DruidDataSourceWrapper dataSource =
                new DruidDataSourceWrapper(dbName, user, passwd, encoding, minPoolSize, maxPoolSize, params,
                    newConnectionSQLs);
            dataSource.init();
            return dataSource;
        } else {
            return createDruidMySqlDataSource(ip, port, dbName, user, passwd, encoding, minPoolSize, maxPoolSize,
                params, newConnectionSQLs);
        }
    }

    private static DruidDataSource createDruidMySqlDataSource(String ip, int port, String dbName, String user,
                                                              String passwd, String encoding, int minPoolSize,
                                                              int maxPoolSize, Map<String, String> params,
                                                              List<String> newConnectionSQLs) throws Exception {
        checkParams(ip, port, user, maxPoolSize, minPoolSize);
        DruidDataSource ds = new DruidDataSource();
        String url = "jdbc:mysql://" + ip + ":" + port;
        if (StringUtils.isNotBlank(dbName)) {
            url = url + "/" + dbName;
        }
        // remove warning msg
        url = url + "?allowPublicKeyRetrieval=true&useSSL=false";
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(passwd);
        ds.setTestWhileIdle(true);
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setNotFullTimeoutRetryCount(2);
        ds.setValidConnectionCheckerClassName(MySqlValidConnectionChecker.class.getName());
        ds.setExceptionSorterClassName(MySqlExceptionSorter.class.getName());
        ds.setValidationQuery("SELECT 1");
        ds.setInitialSize(minPoolSize);
        ds.setMinIdle(minPoolSize);
        ds.setMaxActive(maxPoolSize);
        ds.setMaxWait(10 * 1000);
        ds.setTimeBetweenEvictionRunsMillis(60 * 1000);
        ds.setMinEvictableIdleTimeMillis(50 * 1000);
        ds.setUseUnfairLock(true);
        Properties prop = new Properties();
        encoding = StringUtils.isNotBlank(encoding) ? encoding : "utf8mb4";
        if (StringUtils.isNotEmpty(encoding)) {
            if (StringUtils.equalsIgnoreCase(encoding, "utf8mb4")) {
                prop.put("characterEncoding", "utf8");
                if (newConnectionSQLs == null) {
                    newConnectionSQLs = new ArrayList<>();
                }
                newConnectionSQLs.add("set names utf8mb4");
            } else {
                prop.put("characterEncoding", encoding);
            }
        }

        prop.putAll(DEFAULT_MYSQL_CONNECTION_PROPERTIES);
        if (params != null) {
            prop.putAll(params);
        }
        ds.setConnectProperties(prop);
        if (newConnectionSQLs != null && newConnectionSQLs.size() > 0) {
            ds.setConnectionInitSqls(newConnectionSQLs);
            log.info("druid setConnectionInitSqls, {}, {}", JSON.toJSONString(newConnectionSQLs),
                DEFAULT_MYSQL_CONNECTION_PROPERTIES);
        }
        try {
            ds.init();
        } catch (Exception e) {
            throw new Exception("create druid datasource occur exception, with url : " + url + ", user : " + user
                + ", passwd : " + passwd,
                e);
        }
        return ds;
    }

    private static void checkParams(String ip, int port, String user, int maxPoolSize, int minPoolSize) {
        if (StringUtils.isBlank(ip) || port == 0 || StringUtils.isBlank(user)) {
            throw new IllegalArgumentException("ip or port or schema or user is blank, check the pass params, ip:" + ip
                + ", port:" + port + ", user:" + user);
        }
        if (maxPoolSize < 0 || minPoolSize < 0) {
            throw new IllegalArgumentException("maxPoolSize and minPoolSize must be positive");
        }
    }
}
