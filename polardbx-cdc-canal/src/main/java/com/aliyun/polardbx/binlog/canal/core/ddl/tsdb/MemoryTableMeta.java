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
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.polardbx.druid.sql.ast.SQLExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLIndex;
import com.alibaba.polardbx.druid.sql.ast.SQLName;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLNullExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCharacterDataType;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnPrimaryKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnUniqueKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLNotNullConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLNullConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlOrderingExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.alibaba.polardbx.druid.sql.repository.SchemaObjectStoreProvider;
import com.alibaba.polardbx.druid.sql.repository.SchemaRepository;
import com.alibaba.polardbx.druid.util.JdbcConstants;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * 基于DDL维护的内存表结构
 *
 * @author agapple 2017年7月27日 下午4:19:40
 * @since 3.2.5
 */
public class MemoryTableMeta implements TableMetaTSDB {

    private static final String _DRDS_IMPLICIT_ID_ = "_drds_implicit_id_";
    private static final int DEFAULT_MAX_CACHE_SIZE = 8192;
    private static final int DEFAULT_CACHE_EXPIRE_TIME_MINUTES = 60;

    private final Cache<List<String>, TableMeta> tableMetas;
    private final Logger logger;
    private final boolean ignoreApplyError;
    private boolean ignoreImplicitPrimaryKey;
    protected SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
    protected boolean isMySql8 = false;

    public MemoryTableMeta(Logger logger, boolean ignoreApplyError) {
        this(logger,
            DEFAULT_MAX_CACHE_SIZE,
            DEFAULT_CACHE_EXPIRE_TIME_MINUTES,
            ignoreApplyError);
    }

    public MemoryTableMeta(Logger logger, SchemaObjectStoreProvider provider, int maxCacheSize,
                           int cacheExpireTimeMinutes, boolean ignoreApplyError) {
        this(logger, maxCacheSize, cacheExpireTimeMinutes, ignoreApplyError);
        this.repository.setSchemaObjectStoreProvider(provider);
    }

    private MemoryTableMeta(Logger logger, int maxCacheSize, int expireTimeMinutes, boolean ignoreApplyError) {
        this.logger = logger;
        this.tableMetas = CacheBuilder.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfterWrite(expireTimeMinutes, TimeUnit.MINUTES)
            .build();
        this.ignoreApplyError = ignoreApplyError;
    }

    public void setMySql8(boolean mySql8) {
        isMySql8 = mySql8;
    }

    @Override
    public boolean init(String destination) {
        return true;
    }

    @Override
    public void destroy() {
        tableMetas.invalidateAll();
        repository = new SchemaRepository(JdbcConstants.MYSQL);
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        if (StringUtils.isBlank(ddl)) {
            return true;
        }
        ddl = ddl.toLowerCase();
        ddl = tryRepairSql(ddl);
        tableMetas.invalidateAll();
        synchronized (this) {
            if (StringUtils.isNotEmpty(schema)) {
                repository.setDefaultSchema(schema);
            }

            try {
                // druid暂时flush privileges语法解析有问题
                if (!StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "flush")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "grant")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "create user")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "drop user")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "alter user")) {
                    repository.console(ddl, SQLUtils.SQL_FEATURES);
                    tryRemoveSchema(position, ddl, schema);
                }
            } catch (Throwable e) {
                if (ignoreApplyError) {
                    logger.error("parse failed : " + ddl, e);
                } else {
                    throw new PolardbxException("Table meta apply failed, schema: " + schema + ", ddl: " + ddl, e);
                }
            }
        }

        return true;
    }

    @Override
    public TableMeta find(String schema, String table) {
        try {
            List<String> keys = Arrays.asList(schema, table);
            TableMeta tableMeta = tableMetas.getIfPresent(keys);
            if (tableMeta == null) {
                synchronized (this) {
                    tableMeta = tableMetas.getIfPresent(keys);
                    if (tableMeta == null) {
                        Schema schemaRep = repository.findSchema(schema);
                        if (schemaRep == null) {
                            return null;
                        }
                        SchemaObject data = schemaRep.findTable(table);
                        if (data == null) {
                            return null;
                        }
                        SQLStatement statement = data.getStatement();
                        if (statement == null) {
                            return null;
                        }
                        if (statement instanceof SQLCreateTableStatement) {
                            tableMeta = parse((SQLCreateTableStatement) statement);
                        }
                        if (tableMeta != null) {
                            if (table != null) {
                                tableMeta.setTable(table);
                            }
                            if (schema != null) {
                                tableMeta.setSchema(schema);
                            }

                            tableMetas.put(keys, tableMeta);
                        }
                    }
                }
            }
            return tableMeta;
        } catch (Throwable t) {
            throw new PolardbxException(
                String.format("find table meta failed, schema %s, table %s.", schema, table), t);
        }
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        throw new RuntimeException("not support for memory");
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> schemaDdls = new HashMap<String, String>();
        for (Schema schema : repository.getSchemas()) {
            StringBuffer data = new StringBuffer(4 * 1024);
            for (String table : schema.showTables()) {
                SchemaObject schemaObject = schema.findTable(table);
                SQLStatement statement = schemaObject.getStatement();
                if (statement instanceof MySqlCreateTableStatement) {
                    ((MySqlCreateTableStatement) statement).normalizeTableOptions();
                }
                statement.output(data);
                data.append("; \n");
            }
            schemaDdls.put(schema.getName(), data.toString());
        }

        return schemaDdls;
    }

    public String snapshot(String schemaName, String tableName) {
        try {
            Schema schema = repository.findSchema(schemaName);
            SchemaObject schemaObject = schema.findTable(tableName);
            StringBuffer data = new StringBuffer(1024);
            schemaObject.getStatement().output(data);
            data.append(";");
            return data.toString();
        } catch (Throwable t) {
            throw new PolardbxException(String.format("get snapshot failed, schemaName :%s , tableName %s.",
                schemaName, tableName));
        }
    }

    private TableMeta parse(SQLCreateTableStatement statement) {
        int size = statement.getTableElementList().size();
        if (size > 0) {
            TableMeta tableMeta = new TableMeta();
            for (SQLAssignItem tableOption : statement.getTableOptions()) {
                if (tableOption != null) {
                    if (!(tableOption.getTarget() instanceof SQLIdentifierExpr)) {
                        continue;
                    }
                    String targetName = ((SQLIdentifierExpr) tableOption.getTarget()).getName();
                    if ("CHARACTER SET".equalsIgnoreCase(targetName) || "CHARSET".equalsIgnoreCase(targetName)) {
                        tableMeta.setCharset(((SQLIdentifierExpr) tableOption.getValue()).getName());
                        break;
                    }
                }
            }

            // 先对SQLColumnDefinition进行一轮处理
            for (int i = 0; i < size; ++i) {
                SQLTableElement element = statement.getTableElementList().get(i);
                if (element instanceof SQLColumnDefinition) {
                    processTableElement(element, tableMeta);
                }
            }
            // 再对MySqlPrimaryKey和MySqlUnique进行处理，避免当MySqlPrimaryKey或MySqlUnique比对应的SQLColumnDefinition位置还靠前时触发NPE
            for (int i = 0; i < size; ++i) {
                SQLTableElement element = statement.getTableElementList().get(i);
                if (element instanceof MySqlPrimaryKey || element instanceof MySqlUnique) {
                    processTableElement(element, tableMeta);
                }
            }

            tryFilterImplicitPrimaryKey(tableMeta);
            return tableMeta;
        }

        return null;
    }

    private void tryFilterImplicitPrimaryKey(TableMeta tableMeta) {
        if (ignoreImplicitPrimaryKey) {
            tableMeta.getFields().removeIf(i -> StringUtils.equalsIgnoreCase(i.getColumnName(), _DRDS_IMPLICIT_ID_));
        }

        // see com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta_ImplicitPk_Test.testDropImplicitPk
        List<FieldMeta> primaryKeys = tableMeta.getPrimaryFields();
        Optional<FieldMeta> optional = primaryKeys.stream()
            .filter(i -> StringUtils.equalsIgnoreCase(i.getColumnName(), _DRDS_IMPLICIT_ID_)).findAny();
        if (optional.isPresent() && tableMeta.getPrimaryFields().size() > 1) {
            optional.get().setKey(false);
        }
    }

    private void processTableElement(SQLTableElement element, TableMeta tableMeta) {
        if (element instanceof SQLColumnDefinition) {
            FieldMeta fieldMeta = new FieldMeta();
            SQLColumnDefinition column = (SQLColumnDefinition) element;
            String name = getSqlName(column.getName());
            SQLDataType dataType = column.getDataType();
            String dataTypStr = buildDataTypeStr(dataType.getName());
            dataTypStr = contactPrecision(dataTypStr, column);
            dataTypStr = convertDataTypeStr(dataType, dataTypStr);

            if (dataType instanceof SQLDataTypeImpl) {
                SQLDataTypeImpl dataTypeImpl = (SQLDataTypeImpl) dataType;

                //当使用zerofill 时，MySQL默认会自动加unsigned(无符号)属性，如果通过sql解析出来没有的话，做补齐处理
                if (dataTypeImpl.isUnsigned() || (!dataTypeImpl.isUnsigned() && dataTypeImpl.isZerofill())) {
                    dataTypStr += " unsigned";
                }

                if (dataTypeImpl.isZerofill()) {
                    dataTypStr += " zerofill";
                }
            }

            if (column.getDefaultExpr() == null || column.getDefaultExpr() instanceof SQLNullExpr) {
                fieldMeta.setDefaultValue(null);
            } else {
                // 处理一下default value中特殊的引号
                fieldMeta.setDefaultValue(
                    com.alibaba.polardbx.druid.sql.SQLUtils.normalize(getSqlName(column.getDefaultExpr())));
            }
            if (dataType instanceof SQLCharacterDataType) {
                final String charSetName = ((SQLCharacterDataType) dataType).getCharSetName();
                if (StringUtils.isNotEmpty(charSetName)) {
                    fieldMeta.setCharset(charSetName);
                } else {
                    SQLExpr expr = ((SQLColumnDefinition) element).getCharsetExpr();
                    if (expr instanceof SQLIdentifierExpr) {
                        if (StringUtils.isNotEmpty(((SQLIdentifierExpr) expr).getName())) {
                            fieldMeta.setCharset(((SQLIdentifierExpr) expr).getName());
                        }
                    }
                    if (expr instanceof SQLCharExpr) {
                        if (StringUtils.isNotEmpty(((SQLCharExpr) expr).getText())) {
                            fieldMeta.setCharset(((SQLCharExpr) expr).getText());
                        }
                    }
                }
            }

            // try fill charset for special scene
            if (StringUtils.isBlank(fieldMeta.getCharset())) {
                String charset = buildCharset(dataType.getName());
                if (StringUtils.isNotBlank(charset)) {
                    fieldMeta.setCharset(charset);
                }
            }

            fieldMeta.setColumnName(name);
            fieldMeta.setColumnType(dataTypStr);
            fieldMeta.setNullable(true);
            List<SQLColumnConstraint> constraints = column.getConstraints();
            for (SQLColumnConstraint constraint : constraints) {
                if (constraint instanceof SQLNotNullConstraint) {
                    fieldMeta.setNullable(false);
                } else if (constraint instanceof SQLNullConstraint) {
                    fieldMeta.setNullable(true);
                } else if (constraint instanceof SQLColumnPrimaryKey) {
                    fieldMeta.setKey(true);
                    fieldMeta.setNullable(false);
                } else if (constraint instanceof SQLColumnUniqueKey) {
                    fieldMeta.setUnique(true);
                }
            }
            fieldMeta.setGenerated(column.getGeneratedAlawsAs() != null);
            tableMeta.addFieldMeta(fieldMeta);
        } else if (element instanceof MySqlPrimaryKey) {
            MySqlPrimaryKey column = (MySqlPrimaryKey) element;
            List<SQLSelectOrderByItem> pks = column.getColumns();
            for (SQLSelectOrderByItem pk : pks) {
                String name = getSqlName(pk.getExpr());
                FieldMeta field = tableMeta.getFieldMetaByName(name);
                field.setKey(true);
                field.setNullable(false);
            }
        } else if (element instanceof MySqlUnique) {
            MySqlUnique column = (MySqlUnique) element;
            List<SQLSelectOrderByItem> uks = column.getColumns();
            for (SQLSelectOrderByItem uk : uks) {
                String name = getSqlName(uk.getExpr());
                FieldMeta field = tableMeta.getFieldMetaByName(name);
                field.setUnique(true);
            }
        }
    }

    private String getSqlName(SQLExpr sqlName) {
        if (sqlName == null) {
            return null;
        }

        if (sqlName instanceof SQLPropertyExpr) {
            SQLIdentifierExpr owner = (SQLIdentifierExpr) ((SQLPropertyExpr) sqlName).getOwner();
            return com.alibaba.polardbx.druid.sql.SQLUtils
                .normalize(owner.getName()) + "."
                + com.alibaba.polardbx.druid.sql.SQLUtils.normalize(((SQLPropertyExpr) sqlName).getName());
        } else if (sqlName instanceof SQLIdentifierExpr) {
            return com.alibaba.polardbx.druid.sql.SQLUtils.normalize(((SQLIdentifierExpr) sqlName).getName());
        } else if (sqlName instanceof SQLCharExpr) {
            return ((SQLCharExpr) sqlName).getText();
        } else if (sqlName instanceof SQLMethodInvokeExpr) {
            return com.alibaba.polardbx.druid.sql.SQLUtils.normalize(((SQLMethodInvokeExpr) sqlName).getMethodName());
        } else if (sqlName instanceof MySqlOrderingExpr) {
            return getSqlName(((MySqlOrderingExpr) sqlName).getExpr());
        } else {
            return sqlName.toString();
        }
    }

    public boolean isSchemaExists(String schema) {
        Schema schemaRep = repository.findSchema(schema);
        return schemaRep != null;
    }

    public Set<String> findIndexes(String schema, String table) {
        Set<String> result = new HashSet<>();

        Schema schemaRep = repository.findSchema(schema);
        if (schemaRep == null) {
            return result;
        }

        SchemaObject data = schemaRep.findTable(table);
        if (data == null) {
            return result;
        }

        SQLStatement statement = data.getStatement();
        if (statement == null) {
            return result;
        }

        if (statement instanceof SQLCreateTableStatement) {
            SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) statement;
            sqlCreateTableStatement.getTableElementList().forEach(e -> {
                if (e instanceof SQLConstraint && e instanceof SQLIndex) {
                    SQLConstraint sqlConstraint = (SQLConstraint) e;
                    if (sqlConstraint.getName() != null) {
                        result.add(
                            com.alibaba.polardbx.druid.sql.SQLUtils.normalize(sqlConstraint.getName().getSimpleName()));
                    }
                } else if (e instanceof SQLColumnDefinition) {
                    SQLColumnDefinition columnDefinition = (SQLColumnDefinition) e;
                    List<SQLColumnConstraint> constraints = columnDefinition.getConstraints();
                    if (constraints != null) {
                        for (SQLColumnConstraint constraint : constraints) {
                            if (constraint instanceof SQLColumnUniqueKey) {
                                result.add(com.alibaba.polardbx.druid.sql.SQLUtils
                                    .normalize(columnDefinition.getName().getSimpleName()));
                            }
                        }
                    }
                }
            });
        }

        Collection<SchemaObject> objects = schemaRep.getIndexes();
        if (objects != null) {
            objects.forEach(o -> {
                if (o.getStatement() instanceof SQLCreateIndexStatement) {
                    SQLCreateIndexStatement createIndexStatement = (SQLCreateIndexStatement) o.getStatement();
                    String indexTable =
                        com.alibaba.polardbx.druid.sql.SQLUtils.normalize(createIndexStatement.getTableName());
                    if (StringUtils.equalsIgnoreCase(indexTable, table)) {
                        SQLName sqlName = createIndexStatement.getIndexDefinition().getName();
                        if (sqlName != null) {
                            result.add(com.alibaba.polardbx.druid.sql.SQLUtils.normalize(sqlName.getSimpleName()));
                        }
                    }
                }
            });
        }

        return result;
    }

    private void tryRemoveSchema(BinlogPosition position, String ddlSql, String schema) throws IllegalAccessException {
        SQLStatement stmt = parseSQLStatement(ddlSql);

        if (stmt instanceof SQLDropDatabaseStatement) {
            Class<?> clazz = SchemaRepository.class;
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String name = field.getName();
                if ("schemas".equalsIgnoreCase(name)) {
                    field.setAccessible(true);
                    Map schemas = (Map) field.get(repository);
                    if (schemas.containsKey(schema)) {
                        Schema schemaObj = (Schema) schemas.get(schema);
                        schemaObj.getStore().clearAll();
                        schemas.remove(schema);

                        if (logger.isDebugEnabled()) {
                            logger.debug("schema is removed from schema repository, tso {}, schema {}, ddlSql {}.",
                                position == null ? "" : position.getRtso(), schema, ddlSql);
                        }
                    }
                    break;
                }
            }
            repository.setDefaultSchema((Schema) null);
        }
    }

    private String contactPrecision(String dataTypStr, SQLColumnDefinition column) {
        if (column.getDataType().getArguments().size() > 0) {
            String origin = dataTypStr;
            dataTypStr += "(";
            for (int i = 0; i < column.getDataType().getArguments().size(); i++) {
                if (i != 0) {
                    dataTypStr += ",";
                }
                SQLExpr arg = column.getDataType().getArguments().get(i);
                dataTypStr += arg.toString().trim();
            }
            if (StringUtils.equalsIgnoreCase(origin, "decimal") && column.getDataType().getArguments().size() == 1) {
                dataTypStr += ",0";
            }
            dataTypStr += ")";
        }
        return dataTypStr;
    }

    private String convertDataTypeStr(SQLDataType dataType, String dataTypeStr) {
        boolean unsigned = false;
        if (dataType instanceof SQLDataTypeImpl) {
            //当使用zerofill 时，MySQL默认会自动加unsigned(无符号)属性
            SQLDataTypeImpl dataTypeImpl = (SQLDataTypeImpl) dataType;
            unsigned = dataTypeImpl.isUnsigned() || dataTypeImpl.isZerofill();
        }

        if ("tinyint".equalsIgnoreCase(dataTypeStr) || "tinyint(0)".equalsIgnoreCase(dataTypeStr)) {
            return convertTinyInt(unsigned);

        } else if ("smallint".equalsIgnoreCase(dataTypeStr) || "smallint(0)".equalsIgnoreCase(dataTypeStr)) {
            return convertSmallint(unsigned);

        } else if ("mediumint".equalsIgnoreCase(dataTypeStr) || "mediumint(0)".equalsIgnoreCase(dataTypeStr)) {
            return convertMediumInt(unsigned);

        } else if ("int".equalsIgnoreCase(dataTypeStr) || "int(0)".equalsIgnoreCase(dataTypeStr)) {
            return convertInt(unsigned);

        } else if ("bigint".equalsIgnoreCase(dataTypeStr) || "bigint(0)".equalsIgnoreCase(dataTypeStr)) {
            return "bigint(20)";

        } else if ("decimal".equalsIgnoreCase(dataTypeStr) || "decimal(0)".equalsIgnoreCase(dataTypeStr)
            || "decimal(0,0)".equalsIgnoreCase(dataTypeStr)) {
            return "decimal(10,0)";

        } else if ("bit".equalsIgnoreCase(dataTypeStr)) {
            return "bit(1)";

        } else if ("boolean".equalsIgnoreCase(dataTypeStr)) {
            return "tinyint(1)";

        } else if ("year".equalsIgnoreCase(dataTypeStr)) {
            return "year(4)";

        } else if ("binary".equalsIgnoreCase(dataTypeStr)) {
            return "binary(1)";

        } else if ("char".equalsIgnoreCase(dataTypeStr)) {
            return "char(1)";

        } else if ("datetime(0)".equalsIgnoreCase(dataTypeStr)) {
            return "datetime";

        } else if ("timestamp(0)".equalsIgnoreCase(dataTypeStr)) {
            return "timestamp";

        } else if ("time(0)".equalsIgnoreCase(dataTypeStr)) {
            return "time";

        } else if ("geomcollection".equalsIgnoreCase(dataTypeStr)) {
            return "geometrycollection";

        } else if (StringUtils.startsWithIgnoreCase(dataTypeStr, "blob(")) {
            return convertBlob(dataTypeStr);

        } else if (StringUtils.startsWithIgnoreCase(dataTypeStr, "text(")) {
            return convertText(dataTypeStr);

        } else {
            return dataTypeStr;
        }
    }

    private String buildCharset(String dataType) {
        // see issue:49619404
        if ("nchar".equalsIgnoreCase(dataType)) {
            return "utf8";
        } else if ("nvarchar".equalsIgnoreCase(dataType)) {
            return "utf8";
        }
        return null;
    }

    private String buildDataTypeStr(String dataTypeStr) {
        if (StringUtils.equalsIgnoreCase(dataTypeStr, "dec")
            || StringUtils.equalsIgnoreCase(dataTypeStr, "numeric")) {
            return "decimal";
        } else if (StringUtils.equalsIgnoreCase(dataTypeStr, "integer")) {
            return "int";
        } else if (StringUtils.equalsIgnoreCase(dataTypeStr, "nchar")) {
            return "char";
        } else if (StringUtils.equalsIgnoreCase(dataTypeStr, "nvarchar")) {
            return "varchar";
        } else {
            return dataTypeStr;
        }
    }

    private String convertTinyInt(boolean unsigned) {
        if (unsigned) {
            return "tinyint(3)";
        } else {
            return "tinyint(4)";
        }
    }

    private String convertSmallint(boolean unsigned) {
        if (unsigned) {
            return "smallint(5)";
        } else {
            return "smallint(6)";
        }
    }

    private String convertMediumInt(boolean unsigned) {
        if (unsigned) {
            return "mediumint(8)";
        } else {
            return "mediumint(9)";
        }
    }

    private String convertInt(boolean unsigned) {
        if (unsigned) {
            return "int(10)";
        } else {
            return "int(11)";
        }
    }

    private String convertBlob(String str) {
        str = StringUtils.substringAfter(str, "(");
        str = StringUtils.substringBefore(str, ")");
        long length = Long.parseLong(str);
        if (!isMySql8 && length == 0L) {
            return "blob";
        }

        if (length <= 255) {
            return "tinyblob";
        } else if (length <= 65535) {
            return "blob";
        } else if (length <= 16777215) {
            return "mediumblob";
        } else {
            return "longblob";
        }
    }

    private String convertText(String str) {
        str = StringUtils.substringAfter(str, "(");
        str = StringUtils.substringBefore(str, ")");
        long length = Long.parseLong(str.trim());
        if (!isMySql8 && length == 0L) {
            return "text";
        }

        if (length <= 63) {
            return "tinytext";
        } else if (length <= 16383) {
            return "text";
        } else if (length <= 4194303) {
            return "mediumtext";
        } else {
            return "longtext";
        }
    }

    // see aone : 46916964
    String tryRepairSql(String sql) {
        if (StringUtils.containsIgnoreCase(sql, _DRDS_IMPLICIT_ID_)) {
            SQLStatement sqlStatement = parseSQLStatement(sql);

            if (sqlStatement instanceof MySqlCreateTableStatement) {
                MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
                List<SQLTableElement> sqlTableElementList = createTableStatement.getTableElementList();
                Iterator<SQLTableElement> iterator = sqlTableElementList.iterator();

                boolean containsImplicitCol = false;
                boolean containsImplicitPrimary = false;
                while (iterator.hasNext()) {
                    SQLTableElement element = iterator.next();
                    if (element instanceof SQLColumnDefinition) {
                        SQLColumnDefinition columnDefinition = (SQLColumnDefinition) element;
                        String columnName =
                            com.alibaba.polardbx.druid.sql.SQLUtils.normalize(columnDefinition.getColumnName());
                        if (StringUtils.equalsIgnoreCase(columnName, _DRDS_IMPLICIT_ID_)) {
                            containsImplicitCol = true;
                        }
                    } else if (element instanceof MySqlPrimaryKey) {
                        MySqlPrimaryKey column = (MySqlPrimaryKey) element;
                        List<SQLSelectOrderByItem> pks = column.getColumns();
                        for (SQLSelectOrderByItem pk : pks) {
                            String columnName =
                                com.alibaba.polardbx.druid.sql.SQLUtils.normalize(getSqlName(pk.getExpr()));
                            if (StringUtils.equalsIgnoreCase(columnName, _DRDS_IMPLICIT_ID_)) {
                                containsImplicitPrimary = true;
                            }
                        }
                    }
                }
                if (containsImplicitPrimary && !containsImplicitCol) {
                    SQLColumnDefinition columnDefinition = new SQLColumnDefinition();
                    columnDefinition.setName(_DRDS_IMPLICIT_ID_);
                    columnDefinition.setDataType(new SQLDataTypeImpl("bigint(20)"));
                    columnDefinition.setAutoIncrement(true);
                    sqlTableElementList.add(columnDefinition);
                    return sqlStatement.toLowerCaseString();
                }
            }
        }
        return sql;
    }

    public SchemaRepository getRepository() {
        return repository;
    }

    public boolean isIgnoreImplicitPrimaryKey() {
        return ignoreImplicitPrimaryKey;
    }

    public void setIgnoreImplicitPrimaryKey(boolean ignoreImplicitPrimaryKey) {
        this.ignoreImplicitPrimaryKey = ignoreImplicitPrimaryKey;
    }
}
