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
package com.aliyun.polardbx.binlog.cdc.repository;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.alibaba.polardbx.druid.sql.repository.SchemaObjectStore;
import com.alibaba.polardbx.druid.sql.repository.SchemaObjectType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.aliyun.polardbx.meta.SchemaObjectEntity;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class PersistentSchemaStore implements SchemaObjectStore {
    public static final AtomicLong KEY_PREFIX_SEED = new AtomicLong(0);
    public static final String KEY_TYPE_OBJECT = "OBJECT";
    public static final String KEY_TYPE_FUNCTION = "FUNCTION";
    public static final String KEY_TYPE_INDEX = "INDEX";
    public static final String KEY_TYPE_SEQUENCE = "SEQUENCE";

    private final RepoUnit repoUnit;
    private final String keyPrefix;
    private Schema schema;

    public PersistentSchemaStore(RepoUnit repoUnit) {
        this.keyPrefix = String.valueOf(KEY_PREFIX_SEED.incrementAndGet());
        this.repoUnit = repoUnit;
    }

    private final Set<Long> objectKeySet = new HashSet<>();
    private final Set<Long> functionKeySet = new HashSet<>();
    private final Set<Long> indexKeySet = new HashSet<>();
    private final Set<Long> sequenceKeySet = new HashSet<>();

    private void saveData(Long key, String keyType, SchemaObject schemaObject) {
        byte[] keyBytes = buildKey(key, keyType);
        try {
            StringBuffer sql = new StringBuffer();
            schemaObject.getStatement().output(sql);
            SchemaObjectEntity entity = SchemaObjectEntity.newBuilder()
                .setName(schemaObject.getName())
                .setStmtSql(sql.toString())
                .setType(schemaObject.getType().name()).build();
            repoUnit.put(keyBytes, entity.toByteArray());
        } catch (RocksDBException e) {
            throw new PolardbxException("save meta data failed!", e);
        }
    }

    private SchemaObject loadData(Long key, String keyType) {
        try {
            byte[] data = repoUnit.get(buildKey(key, keyType));
            return parseObject(data);
        } catch (RocksDBException e) {
            throw new PolardbxException("load meta data failed!", e);
        }
    }

    private void deleteData(Long key, String keyType) {
        try {
            repoUnit.delete(buildKey(key, keyType));
        } catch (RocksDBException e) {
            throw new PolardbxException("load meta data failed!", e);
        }
    }

    private byte[] buildKey(Long key, String keyType) {
        String saveKey = keyPrefix + "_" + keyType + "_" + key;
        return ByteUtil.bytes(saveKey);
    }

    private SchemaObject parseObject(byte[] data) {
        try {
            SchemaObjectEntity entity = SchemaObjectEntity.parseFrom(data);
            return new SchemaObject(schema, entity.getName(), SchemaObjectType.valueOf(entity.getType()),
                parseStatement(entity.getStmtSql()));
        } catch (InvalidProtocolBufferException e) {
            throw new PolardbxException("parse meta data failed!", e);
        }
    }

    private SQLStatement parseStatement(String sql) {
        if (log.isDebugEnabled()) {
            log.debug("sql for parse is : " + sql);
        }
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        return statementList.get(0);
    }

    private Collection<SchemaObject> buildAll(Set<Long> allObjKey, String keyType) {
        if (allObjKey.isEmpty()) {
            return new ArrayList<>();
        } else {
            List<byte[]> keys = allObjKey.stream().map(k -> {
                String keyStr = keyPrefix + "_" + keyType + "_" + k;
                return ByteUtil.bytes(keyStr);
            }).collect(Collectors.toList());
            List<byte[]> values;
            try {
                values = repoUnit.multiGet(keys);
            } catch (RocksDBException e) {
                throw new PolardbxException("multi get meta data failed");
            }
            return values.stream().map(this::parseObject).collect(Collectors.toList());
        }
    }

    @Override
    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    @Override
    public void addObject(Long key, SchemaObject schemaObject) {
        try {
            objectKeySet.add(key);
            saveData(key, KEY_TYPE_OBJECT, schemaObject);
        } catch (Throwable t) {
            objectKeySet.remove(key);
            throw t;
        }
    }

    @Override
    public void removeObject(Long key) {
        objectKeySet.remove(key);
        deleteData(key, KEY_TYPE_OBJECT);
    }

    @Override
    public SchemaObject getObject(Long key) {
        if (objectKeySet.contains(key)) {
            return loadData(key, KEY_TYPE_OBJECT);
        }
        return null;
    }

    @SneakyThrows
    @Override
    public Collection<SchemaObject> getAllObjects() {
        return buildAll(objectKeySet, KEY_TYPE_OBJECT);
    }

    @Override
    public void addIndex(Long key, SchemaObject schemaObject) {
        try {
            indexKeySet.add(key);
            saveData(key, KEY_TYPE_INDEX, schemaObject);
        } catch (Throwable t) {
            indexKeySet.remove(key);
            throw t;
        }
    }

    @Override
    public void removeIndex(Long key) {
        indexKeySet.remove(key);
        deleteData(key, KEY_TYPE_INDEX);
    }

    @Override
    public SchemaObject getIndex(Long key) {
        if (indexKeySet.contains(key)) {
            return loadData(key, KEY_TYPE_INDEX);
        }
        return null;
    }

    @Override
    public Collection<SchemaObject> getAllIndexes() {
        return buildAll(indexKeySet, KEY_TYPE_INDEX);
    }

    @Override
    public void addSequence(Long key, SchemaObject schemaObject) {
        try {
            sequenceKeySet.add(key);
            saveData(key, KEY_TYPE_SEQUENCE, schemaObject);
        } catch (Throwable t) {
            sequenceKeySet.remove(key);
            throw t;
        }
    }

    @Override
    public void removeSequence(Long key) {
        sequenceKeySet.remove(key);
        deleteData(key, KEY_TYPE_SEQUENCE);
    }

    @Override
    public SchemaObject getSequence(Long key) {
        if (sequenceKeySet.contains(key)) {
            return loadData(key, KEY_TYPE_SEQUENCE);
        }
        return null;
    }

    @Override
    public Collection<SchemaObject> getAllSequences() {
        return buildAll(sequenceKeySet, KEY_TYPE_INDEX);
    }

    @Override
    public void addFunction(Long key, SchemaObject schemaObject) {
        try {
            functionKeySet.add(key);
            saveData(key, KEY_TYPE_FUNCTION, schemaObject);
        } catch (Throwable t) {
            functionKeySet.remove(key);
            throw t;
        }
    }

    @Override
    public void removeFunction(Long key) {
        functionKeySet.remove(key);
        deleteData(key, KEY_TYPE_FUNCTION);
    }

    @Override
    public SchemaObject getFunction(Long key) {
        if (functionKeySet.contains(key)) {
            return loadData(key, KEY_TYPE_FUNCTION);
        }
        return null;
    }

    @Override
    public Collection<SchemaObject> getAllFunctions() {
        return buildAll(functionKeySet, KEY_TYPE_FUNCTION);
    }

    @Override
    public void clearAll() {
        sequenceKeySet.forEach(i -> deleteData(i, KEY_TYPE_SEQUENCE));
        indexKeySet.forEach(i -> deleteData(i, KEY_TYPE_INDEX));
        functionKeySet.forEach(i -> deleteData(i, KEY_TYPE_FUNCTION));
        objectKeySet.forEach(i -> deleteData(i, KEY_TYPE_OBJECT));

        sequenceKeySet.clear();
        indexKeySet.clear();
        functionKeySet.clear();
        objectKeySet.clear();
    }
}
