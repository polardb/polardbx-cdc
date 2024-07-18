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
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_CONNECTION_INIT_SQL;

/**
 * @author shicai.xsc 2021/1/4 11:02
 * @since 5.0.0.0
 */
@Slf4j
@Data
public class DbMetaCache {

    private static final String POLARX_DEFAULT_SCHEMA = "polardbx";
    private static final String SET_POLARX_SERVER_ID = "set polardbx_server_id=%d";
    private static final String SET_MYSQL_SERVER_ID = "set global server_id=%d";
    private static final String SET_SQL_MODE = "set sql_mode='%s'";

    private final HostInfo hostInfo;
    private final int minPoolSize;
    private final int maxPoolSize;
    private final String sqlMode;
    private boolean enablePolardbxServerId;
    private final boolean longSql;

    private final LoadingCache<String, DruidDataSource> dataSources = CacheBuilder.newBuilder()
        .expireAfterAccess(120, TimeUnit.SECONDS)
        .removalListener(
            (RemovalListener<String, DruidDataSource>) notification -> {
                try {
                    DbMetaCache.this.tableInfos.invalidateAll();
                    DruidDataSource ds = notification.getValue();
                    ds.close();
                    log.info("DbMetaCache: successfully closed datasource for " + notification.getKey());
                } catch (Exception e) {
                    log.error("DbMetaCache: try close datasource failed for " + notification.getKey());
                }
            })
        .build(new CacheLoader<String, DruidDataSource>() {
            @Override
            public DruidDataSource load(@NotNull String key) throws Exception {
                return loadDataSource(key);
            }
        });

    private final LoadingCache<String, TableInfo> tableInfos = CacheBuilder.newBuilder().build(
        new CacheLoader<String, TableInfo>() {
            @Override
            public TableInfo load(@NotNull String key) throws Exception {
                String schema = StringUtils.substringBefore(key, ".");
                String tbName = StringUtils.substringAfter(key, ".");
                DataSource dataSource = getDataSource(schema);
                return DbMetaManager.getTableInfo(dataSource, schema, tbName, hostInfo.getType());
            }
        });

    public DbMetaCache(HostInfo hostInfo, int maxPoolSize, boolean longSql) {
        this(hostInfo, 1, maxPoolSize, longSql);
    }

    public DbMetaCache(HostInfo hostInfo, int minPoolSize, int maxPoolSize, boolean longSql) {
        this.hostInfo = hostInfo;
        this.minPoolSize = minPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.sqlMode = DynamicApplicationConfig.getString(ConfigKeys.RPL_DEFAULT_SQL_MODE);
        this.longSql = longSql;
        this.enablePolardbxServerId = !DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION);

    }

    public DataSource getDataSource(String schema) {
        try {
            if (StringUtils.isEmpty(schema)) {
                return getBuiltInDefaultDataSource();
            }
            return dataSources.getUnchecked(schema.toLowerCase());
        } catch (Exception e) {
            log.error("failed in getDataSource, host: {}, port: {}, schema: {}",
                hostInfo.getHost(),
                hostInfo.getPort(),
                schema);
            throw e;
        }
    }

    private DruidDataSource loadDataSource(String schema) throws Exception {
        log.warn("set server id: {}", Math.abs(new Long(hostInfo.getServerId()).intValue()));
        List<String> connectionInitSqls = prepareConnectionInitSqls();

        // RDS/mysql 可能没有set global 权限，不考虑实现环状复制，因此不需要set server id
        return DataSourceUtil.createDruidMySqlDataSource(hostInfo.isUsePolarxPoolCN(),
            hostInfo.getHost(),
            hostInfo.getPort(),
            schema,
            hostInfo.getUserName(),
            hostInfo.getPassword(),
            "",
            minPoolSize,
            maxPoolSize,
            longSql,
            null,
            connectionInitSqls);
    }

    private List<String> prepareConnectionInitSqls() {
        List<String> connectionInitSqls = new ArrayList<>();
        connectionInitSqls.add(String.format(SET_SQL_MODE, sqlMode));

        if (hostInfo.getType() == HostType.POLARX2 || hostInfo.getType() == HostType.POLARX1) {
            if (enablePolardbxServerId && hostInfo.getServerId() != 0) {
                String setServerIdSql = String.format(SET_POLARX_SERVER_ID,
                    Math.abs(Long.valueOf(hostInfo.getServerId()).intValue()));
                connectionInitSqls.add(setServerIdSql);
            }
        }

        if (hostInfo.getType() == HostType.POLARX2) {
            String connInitSqlConfig = DynamicApplicationConfig.getString(RPL_CONNECTION_INIT_SQL);
            String[] array = StringUtils.split(connInitSqlConfig, ";");
            connectionInitSqls.addAll(Lists.newArrayList(array));
        }
        return connectionInitSqls;
    }

    public DataSource getDefaultDataSource() {
        if (StringUtils.isNotBlank(hostInfo.getSchema())) {
            return getDataSource(hostInfo.getSchema());
        }
        return getBuiltInDefaultDataSource();
    }

    public DataSource getBuiltInDefaultDataSource() {
        String defaultSchema = hostInfo.getType() == HostType.POLARX2 ? POLARX_DEFAULT_SCHEMA : "";
        return getDataSource(defaultSchema);
    }

    public List<String> getDatabases() throws Exception {
        try {
            DataSource dataSource = getBuiltInDefaultDataSource();
            return DbMetaManager.getDatabases(dataSource);
        } catch (Exception e) {
            log.error("failed in getDatabases, host: {}, port: {}", hostInfo.getHost(), hostInfo.getPort());
            throw e;
        }
    }

    public void refreshTableInfo(String schema, String tbName) {
        String key = schema + "." + tbName;
        tableInfos.invalidate(key.toLowerCase());
        log.info("table info is refreshed for {}.", key);
    }

    public void removeDataSource(String schema) {
        dataSources.invalidate(schema.toLowerCase());
        tableInfos.invalidateAll();
        log.info("datasource is removed for {}.", schema);
    }

    public TableInfo getTableInfo(String schema, String tbName) throws Exception {
        String key = schema + "." + tbName;
        try {
            return tableInfos.getUnchecked(key.toLowerCase());
        } catch (Exception e) {
            log.error("failed in getTableInfo, host: {}, port: {}, schema: {}, tbName: {}",
                hostInfo.getHost(), hostInfo.getPort(), schema, tbName);
            throw e;
        }
    }

    public List<String> getTables(String schema) throws Exception {
        try {
            DataSource dataSource = getDataSource(schema);
            return DbMetaManager.getTables(dataSource);
        } catch (Exception e) {
            log.error("failed in getTables, host: {}, port: {}, schema: {}",
                hostInfo.getHost(),
                hostInfo.getPort(),
                schema);
            throw e;
        }
    }

    public String getCreateTable(String schema, String table) throws Exception {
        try {
            DataSource dataSource = getDataSource(schema);
            return DbMetaManager.getCreateTable(dataSource, schema, table);
        } catch (Exception e) {
            log.error("failed in getCreateTable, host: {}, port: {}, schema: {}, table: {}",
                hostInfo.getHost(),
                hostInfo.getPort(),
                schema, table);
            throw e;
        }
    }
}
