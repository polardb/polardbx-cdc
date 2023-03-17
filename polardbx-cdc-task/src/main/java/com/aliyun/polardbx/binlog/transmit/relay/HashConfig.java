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
package com.aliyun.polardbx.binlog.transmit.relay;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XTableStreamMappingDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XTableStreamMappingMapper;
import com.aliyun.polardbx.binlog.domain.po.XTableStreamMapping;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_HASH_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_DB_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_DB_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_RECORD_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_RECORD_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_TABLE_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_USE_TABLE_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.util.FastSQLConstant.FEATURES;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class HashConfig {
    private final static XTableStreamMappingMapper TABLE_STREAM_MAPPER =
        SpringContextHolder.getObject(XTableStreamMappingMapper.class);
    private final static TransactionTemplate TRANSACTION_TEMPLATE =
        SpringContextHolder.getObject("metaTransactionTemplate");

    private final static HashLevel DEFAULT_HASH_LEVEL = HashLevel.valueOf(getString(BINLOG_X_TRANSMIT_HASH_LEVEL));
    private final static int STREAM_COUNT = DynamicApplicationConfig.getInt(BINLOG_X_STREAM_COUNT);
    private final static String MAPPING_KEY = "MAPPING_KEY";

    private final static Set<String> RECORD_LEVEL_HASH_DB_SET = new HashSet<>();
    private final static Set<String> RECORD_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private final static Set<String> DB_LEVEL_HASH_DB_SET = new HashSet<>();
    private final static Set<String> DB_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private final static Set<String> TABLE_LEVEL_HASH_DB_SET = new HashSet<>();
    private final static Set<String> TABLE_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private final static LoadingCache<String, Map<String, Integer>> TABLE_STREAM_MAPPING =
        CacheBuilder.newBuilder().build(
            new CacheLoader<String, Map<String, Integer>>() {
                @Override
                public Map<String, Integer> load(String key) {
                    return buildTableStreamMap();
                }
            }
        );

    private static Map<String, Integer> buildTableStreamMap() {
        List<XTableStreamMapping> list = TABLE_STREAM_MAPPER.select(s -> s
            .where(XTableStreamMappingDynamicSqlSupport.clusterId,
                isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));

        Map<String, Integer> result = new HashMap<>();
        list.forEach(m -> {
            String db = m.getDbName().toLowerCase();
            String table = m.getTableName().toLowerCase();
            String fullName = db + "." + table;
            result.put(fullName, m.getStreamSeq().intValue());
        });
        return result;
    }

    public static void clearTableStreamMapping() {
        TABLE_STREAM_MAPPING.invalidateAll();
    }

    public static void tryReloadTableStreamMapping(TxnToken token) {
        Pair<String, String> pair = parseRenameSql(token.getDdl());
        if (pair != null && getHashLevel(token.getSchema(), pair.getKey()) == HashLevel.TABLE) {
            XTableStreamMapping tsm = getTableStreamMapping(token.getSchema(), pair.getKey());
            TRANSACTION_TEMPLATE.execute(t -> {
                if (tsm == null) {
                    int streamSeq = getStreamSeq(token.getSchema(), pair.getKey(), -1);
                    insertTableStreamMapping(token.getSchema(), pair.getKey(), streamSeq);
                    insertTableStreamMapping(token.getSchema(), pair.getValue(), streamSeq);
                } else {
                    int streamSeq = tsm.getStreamSeq().intValue();
                    insertTableStreamMapping(token.getSchema(), pair.getValue(), streamSeq);
                }
                return null;
            });
            TABLE_STREAM_MAPPING.invalidateAll();
        }
    }

    private static XTableStreamMapping getTableStreamMapping(String db, String table) {
        List<XTableStreamMapping> list = TABLE_STREAM_MAPPER.select(
            s -> s.where(XTableStreamMappingDynamicSqlSupport.dbName, isEqualTo(db))
                .and(XTableStreamMappingDynamicSqlSupport.tableName, isEqualTo(table))
                .and(XTableStreamMappingDynamicSqlSupport.clusterId,
                    isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));
        return list.isEmpty() ? null : list.get(0);
    }

    private static void insertTableStreamMapping(String db, String table, int seq) {
        XTableStreamMapping mapping = new XTableStreamMapping();
        try {
            mapping.setDbName(db);
            mapping.setTableName(table);
            mapping.setStreamSeq((long) seq);
            mapping.setClusterId(DynamicApplicationConfig.getString(CLUSTER_ID));
            TABLE_STREAM_MAPPER.insert(mapping);
        } catch (DuplicateKeyException e) {
            log.warn("duplicate table stream mapping " + mapping);
        }
    }

    private static Pair<String, String> parseRenameSql(String sql) {
        SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);
        if (stmt instanceof MySqlRenameTableStatement) {
            MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) stmt;
            for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                //CN只支持一次Rename一张表，直接返回即可
                return Pair.of(SQLUtils.normalize(item.getName().getSimpleName()),
                    SQLUtils.normalize(item.getTo().getSimpleName()));
            }
        }
        return null;
    }

    static {
        String dbListStr0 = getString(BINLOG_X_USE_RECORD_LEVEL_HASH_DB_LIST);
        if (StringUtils.isNotBlank(dbListStr0)) {
            dbListStr0 = dbListStr0.toLowerCase();
            String[] array = StringUtils.split(dbListStr0, ",");
            RECORD_LEVEL_HASH_DB_SET.addAll(Arrays.asList(array));
        }

        String tableListStr0 = getString(BINLOG_X_USE_RECORD_LEVEL_HASH_TABLE_LIST);
        if (StringUtils.isNotBlank(tableListStr0)) {
            tableListStr0 = tableListStr0.toLowerCase();
            String[] array = StringUtils.split(tableListStr0, ",");
            RECORD_LEVEL_HASH_TABLE_SET.addAll(Arrays.asList(array));
        }

        String dbListStr1 = getString(BINLOG_X_USE_DB_LEVEL_HASH_DB_LIST);
        if (StringUtils.isNotBlank(dbListStr1)) {
            dbListStr1 = dbListStr1.toLowerCase();
            String[] array = StringUtils.split(dbListStr1, ",");
            DB_LEVEL_HASH_DB_SET.addAll(Arrays.asList(array));
        }

        String tableListStr1 = getString(BINLOG_X_USE_DB_LEVEL_HASH_TABLE_LIST);
        if (StringUtils.isNotBlank(tableListStr1)) {
            tableListStr1 = tableListStr1.toLowerCase();
            String[] array = StringUtils.split(tableListStr1, ",");
            DB_LEVEL_HASH_TABLE_SET.addAll(Arrays.asList(array));
        }

        String dbListStr2 = getString(BINLOG_X_USE_TABLE_LEVEL_HASH_DB_LIST);
        if (StringUtils.isNotBlank(dbListStr2)) {
            dbListStr2 = dbListStr2.toLowerCase();
            String[] array = StringUtils.split(dbListStr2, ",");
            TABLE_LEVEL_HASH_DB_SET.addAll(Arrays.asList(array));
        }

        String tableListStr2 = getString(BINLOG_X_USE_TABLE_LEVEL_HASH_TABLE_LIST);
        if (StringUtils.isNotBlank(tableListStr2)) {
            tableListStr2 = tableListStr2.toLowerCase();
            String[] array = StringUtils.split(tableListStr2, ",");
            TABLE_LEVEL_HASH_TABLE_SET.addAll(Arrays.asList(array));
        }
    }

    public static HashLevel getHashLevel(String dbName, String tableName) {
        dbName = StringUtils.isNotBlank(dbName) ? dbName.toLowerCase() : dbName;
        tableName = StringUtils.isNotBlank(tableName) ? tableName.toLowerCase() : tableName;
        String fullTableName = dbName + "." + tableName;

        if (RECORD_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.RECORD;
        }
        if (StringUtils.isNotBlank(tableName) && RECORD_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
            return HashLevel.RECORD;
        }
        if (TABLE_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.TABLE;
        }
        if (StringUtils.isNotBlank(tableName) && TABLE_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
            return HashLevel.TABLE;
        }
        if (TABLE_STREAM_MAPPING.getUnchecked(MAPPING_KEY).containsKey(fullTableName)) {
            return HashLevel.TABLE;
        }
        if (DB_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.DATABASE;
        }
        if (StringUtils.isNotBlank(tableName) && DB_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
            return HashLevel.DATABASE;
        }

        return DEFAULT_HASH_LEVEL;
    }

    public static int getStreamSeq(String dbName, String tableName, int recordLevelHashKey) {
        dbName = StringUtils.isNotBlank(dbName) ? dbName.toLowerCase() : dbName;
        tableName = StringUtils.isNotBlank(tableName) ? tableName.toLowerCase() : tableName;
        String fullTableName = dbName + "." + tableName;

        HashLevel hashLevel = getHashLevel(dbName, tableName);

        if (hashLevel == HashLevel.RECORD) {
            return Math.abs(recordLevelHashKey % STREAM_COUNT);
        } else if (hashLevel == HashLevel.TABLE) {
            return Math.abs(buildTableStreamSeq(fullTableName) % STREAM_COUNT);
        } else if (hashLevel == HashLevel.DATABASE) {
            return Math.abs(dbName.hashCode() % STREAM_COUNT);
        } else {
            throw new PolardbxException("invalid hash level " + DEFAULT_HASH_LEVEL);
        }
    }

    private static int buildTableStreamSeq(String fullTableName) {
        Integer streamSeq = TABLE_STREAM_MAPPING.getUnchecked(MAPPING_KEY).get(fullTableName);
        if (streamSeq != null) {
            return streamSeq;
        } else {
            return fullTableName.hashCode();
        }
    }
}
