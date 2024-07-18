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

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
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
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_DB_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_DB_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RECORD_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RECORD_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TABLE_LEVEL_HASH_DB_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST_REGEX;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class HashConfig {
    private static final XTableStreamMappingMapper TABLE_STREAM_MAPPER =
        SpringContextHolder.getObject(XTableStreamMappingMapper.class);
    private static final TransactionTemplate TRANSACTION_TEMPLATE =
        SpringContextHolder.getObject("metaTransactionTemplate");

    private static final HashLevel DEFAULT_HASH_LEVEL = HashLevel.valueOf(getString(BINLOGX_TRANSMIT_HASH_LEVEL));
    private static final int STREAM_COUNT = DynamicApplicationConfig.getInt(BINLOGX_STREAM_COUNT);
    private static final String MAPPING_KEY = "MAPPING_KEY";

    private static final Set<String> RECORD_LEVEL_HASH_DB_SET = new HashSet<>();
    private static final Set<String> RECORD_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private static final Set<String> DB_LEVEL_HASH_DB_SET = new HashSet<>();
    private static final Set<String> DB_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private static final Set<String> TABLE_LEVEL_HASH_DB_SET = new HashSet<>();
    private static final Set<String> TABLE_LEVEL_HASH_TABLE_SET = new HashSet<>();
    private static final Set<Pattern> TABLE_LEVEL_HASH_TABLE_REGEX_PATTERN_SET = new HashSet<>();
    private static final LoadingCache<String, Map<String, Integer>> TABLE_STREAM_MAPPING =
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
        SQLStatement stmt = parseSQLStatement(sql);
        if (stmt == null) {
            return null;
        }

        if (stmt instanceof MySqlRenameTableStatement) {
            MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) stmt;
            for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                //CN只支持一次Rename一张表，直接返回即可
                return Pair.of(SQLUtils.normalizeNoTrim(item.getName().getSimpleName()),
                    SQLUtils.normalizeNoTrim(item.getTo().getSimpleName()));
            }
        }
        return null;
    }

    static {
        setRecordLevelHashDbSet(getString(BINLOGX_RECORD_LEVEL_HASH_DB_LIST));
        setRecordLevelHashTableSet(getString(BINLOGX_RECORD_LEVEL_HASH_TABLE_LIST));
        setTableLevelHashDbSet(getString(BINLOGX_TABLE_LEVEL_HASH_DB_LIST));
        setTableLevelHashTableSet(getString(BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST));
        setDbLevelHashDbSet(getString(BINLOGX_DB_LEVEL_HASH_DB_LIST));
        setDbLevelHashTableSet(getString(BINLOGX_DB_LEVEL_HASH_TABLE_LIST));
        setTableLevelHashTableRegexPatternSet(getString(BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST_REGEX));
    }

    public static HashLevel getHashLevel(String dbName, String tableName) {
        dbName = StringUtils.isNotBlank(dbName) ? dbName.toLowerCase() : dbName;
        tableName = StringUtils.isNotBlank(tableName) ? tableName.toLowerCase() : tableName;
        String fullTableName = dbName + "." + tableName;

        // 库级拆分最优先，如果某个库已经设定为库级拆分，则该库和其下面的表，不能再单独设置为其它模式的拆分规则
        if (DB_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.DATABASE;
        }
        if (StringUtils.isNotBlank(tableName) && DB_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
            return HashLevel.DATABASE;
        }

        // 表级别拆分其次
        if (TABLE_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.TABLE;
        }
        if (StringUtils.isNotBlank(tableName)) {
            if (TABLE_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
                return HashLevel.TABLE;
            }
            for (Pattern pattern : TABLE_LEVEL_HASH_TABLE_REGEX_PATTERN_SET) {
                if (pattern.matcher(fullTableName).matches()) {
                    return HashLevel.TABLE;
                }
            }
        }
        if (TABLE_STREAM_MAPPING.getUnchecked(MAPPING_KEY).containsKey(fullTableName)) {
            return HashLevel.TABLE;
        }

        // 记录级拆分优先级最后
        if (RECORD_LEVEL_HASH_DB_SET.contains(dbName)) {
            return HashLevel.RECORD;
        }
        if (StringUtils.isNotBlank(tableName) && RECORD_LEVEL_HASH_TABLE_SET.contains(fullTableName)) {
            return HashLevel.RECORD;
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

    private static void setRecordLevelHashDbSet(String dbList) {
        setHashLevelSetHelper(dbList, RECORD_LEVEL_HASH_DB_SET);
    }

    private static void setRecordLevelHashTableSet(String tbList) {
        setHashLevelSetHelper(tbList, RECORD_LEVEL_HASH_TABLE_SET);
    }

    private static void setTableLevelHashDbSet(String dbList) {
        setHashLevelSetHelper(dbList, TABLE_LEVEL_HASH_DB_SET);
    }

    private static void setTableLevelHashTableSet(String tbList) {
        setHashLevelSetHelper(tbList, TABLE_LEVEL_HASH_TABLE_SET);
    }

    private static void setDbLevelHashDbSet(String dbList) {
        setHashLevelSetHelper(dbList, DB_LEVEL_HASH_DB_SET);
    }

    private static void setDbLevelHashTableSet(String tbList) {
        setHashLevelSetHelper(tbList, DB_LEVEL_HASH_TABLE_SET);
    }

    private static void setHashLevelSetHelper(String list, Set<String> set) {
        if (StringUtils.isBlank(list)) {
            return;
        }
        set.clear();
        list = list.toLowerCase();
        String[] array = StringUtils.split(list, ",");
        set.addAll(Arrays.asList(array));
    }

    private static void setTableLevelHashTableRegexPatternSet(String list) {
        if (StringUtils.isBlank(list)) {
            return;
        }
        TABLE_LEVEL_HASH_TABLE_REGEX_PATTERN_SET.clear();
        String[] array = StringUtils.split(list, ",");
        for (String regex : array) {
            TABLE_LEVEL_HASH_TABLE_REGEX_PATTERN_SET.add(Pattern.compile(regex));
        }
    }
}
