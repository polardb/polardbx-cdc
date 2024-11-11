/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.polardbx.druid.sql.ast.SQLExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLIndex;
import com.alibaba.polardbx.druid.sql.ast.SQLName;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOpExpr;
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
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlOrderingExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
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
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalize;
import static com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim;
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
    @Getter
    @Setter
    private boolean ignoreImplicitPrimaryKey;
    @Getter
    @Setter
    private boolean forceReplace;

    @Getter
    protected SchemaRepository repository;
    protected boolean isMySql8 = false;

    public MemoryTableMeta(Logger logger, boolean ignoreApplyError) {
        this(logger, DEFAULT_MAX_CACHE_SIZE, DEFAULT_CACHE_EXPIRE_TIME_MINUTES, ignoreApplyError);
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
        this.repository = new SchemaRepository(JdbcConstants.MYSQL);
        // polarx 默认设置为utf8mb4
        this.repository.setDefaultCharset("utf8mb4");
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
                if (canApply(schema, ddl)) {
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
                            processIndexMeta(tableMeta, schemaRep, statement);
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

    private boolean canApply(String schemaName, String ddl) {
        boolean flag = !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "flush")
            && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "grant")
            && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "create user")
            && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "drop user")
            && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "alter user");

        if (flag) {
            SQLStatement statement = SQLUtils.parseSQLStatement(ddl);
            if (statement instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) statement;
                String tableName = normalizeNoTrim(sqlCreateTableStatement.getTableName());
                if (sqlCreateTableStatement.isIfNotExists()) {
                    return forceReplace || find(schemaName, tableName) == null;
                }
            }
        }

        return flag;
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
                        if (tableOption.getValue() instanceof SQLBinaryOpExpr) {
                            SQLBinaryOpExpr binaryOpExpr = (SQLBinaryOpExpr) tableOption.getValue();
                            tableMeta.setCharset(binaryOpExpr.getLeft().toString());
                        } else {
                            tableMeta.setCharset(((SQLIdentifierExpr) tableOption.getValue()).getName());
                        }
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
                fieldMeta.setDefaultValue(normalize(getSqlName(column.getDefaultExpr())));
            }
            if (dataType instanceof SQLCharacterDataType) {
                final String charSetName = ((SQLCharacterDataType) dataType).getCharSetName();
                if (StringUtils.isNotEmpty(charSetName)) {
                    fieldMeta.setCharset(charSetName);
                } else {
                    SQLExpr expr = ((SQLColumnDefinition) element).getCharsetExpr();
                    String charset = getCharset(expr);
                    if (StringUtils.isNotBlank(charset)) {
                        fieldMeta.setCharset(charset);
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

            if (StringUtils.isBlank(fieldMeta.getCharset()) && column.getCharsetExpr() != null) {
                SQLExpr expr = column.getCharsetExpr();
                String charset = getCharset(expr);
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
            fieldMeta.setIsOnUpdate(column.getOnUpdate() != null);
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
                // see com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMetaBase.testGenerateColumnIndex
                String name = getSqlName(uk.getExpr());
                FieldMeta field = tableMeta.getFieldMetaByName(name, true);
                if (field != null) {
                    field.setUnique(true);
                }
            }
        }
    }

    private void processIndexMeta(TableMeta tableMeta, Schema schemaRep, SQLStatement statement) {
        if (statement instanceof SQLCreateTableStatement) {
            SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) statement;
            sqlCreateTableStatement.getTableElementList().forEach(e -> {
                if (e instanceof SQLConstraint && e instanceof SQLIndex) {
                    if (e instanceof MySqlTableIndex) {
                        MySqlTableIndex tableIndex = (MySqlTableIndex) e;
                        // 考虑兼容性问题，历史版本index可能没有名字
                        if (tableIndex.getName() != null) {
                            TableMeta.IndexMeta indexMeta = new TableMeta.IndexMeta(
                                normalize(tableIndex.getName().getSimpleName()),
                                tableIndex.getIndexType(),
                                tableIndex.isColumnar());
                            tableMeta.getIndexes().put(indexMeta.getIndexName(), indexMeta);
                        }
                    } else if (e instanceof MySqlKey) {
                        if (!(e instanceof MySqlPrimaryKey)) {
                            MySqlKey mySqlKey = (MySqlKey) e;
                            // 考虑兼容性问题，历史版本index可能没有名字
                            if (mySqlKey.getName() != null) {
                                TableMeta.IndexMeta indexMeta = new TableMeta.IndexMeta(
                                    normalize(mySqlKey.getName().getSimpleName()),
                                    mySqlKey.getIndexType(),
                                    mySqlKey.getIndexDefinition().isColumnar());
                                tableMeta.getIndexes().put(indexMeta.getIndexName(), indexMeta);
                            }
                        }
                    } else {
                        SQLConstraint sqlConstraint = (SQLConstraint) e;
                        // 考虑兼容性问题，历史版本index可能没有名字
                        if (sqlConstraint.getName() != null) {
                            TableMeta.IndexMeta indexMeta = new TableMeta.IndexMeta(
                                normalize(sqlConstraint.getName().getSimpleName()), "", false);
                            tableMeta.getIndexes().put(indexMeta.getIndexName(), indexMeta);
                        }
                    }
                } else if (e instanceof SQLColumnDefinition) {
                    SQLColumnDefinition columnDefinition = (SQLColumnDefinition) e;
                    List<SQLColumnConstraint> constraints = columnDefinition.getConstraints();
                    if (constraints != null) {
                        for (SQLColumnConstraint constraint : constraints) {
                            // 考虑兼容性问题，历史版本index可能没有名字
                            if ((constraint instanceof SQLColumnUniqueKey)) {
                                TableMeta.IndexMeta indexMeta = new TableMeta.IndexMeta(
                                    normalize(columnDefinition.getName().getSimpleName()), "", false);
                                tableMeta.getIndexes().put(indexMeta.getIndexName(), indexMeta);
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
                    String indexTable = normalize(createIndexStatement.getTableName());
                    if (StringUtils.equalsIgnoreCase(indexTable, tableMeta.getTable())) {
                        SQLName sqlName = createIndexStatement.getIndexDefinition().getName();
                        // 考虑兼容性问题，历史版本index可能没有名字
                        if (sqlName != null) {
                            TableMeta.IndexMeta indexMeta = new TableMeta.IndexMeta(
                                normalize(sqlName.getSimpleName()), createIndexStatement.getType(),
                                createIndexStatement.isColumnar());
                            tableMeta.getIndexes().put(indexMeta.getIndexName(), indexMeta);
                        }
                    }
                }
            });
        }
    }

    private String getCharset(SQLExpr expr) {
        String charset = null;
        if (expr instanceof SQLIdentifierExpr) {
            charset = ((SQLIdentifierExpr) expr).getName();
            if (StringUtils.containsIgnoreCase(charset, "collate")) {
                charset = charset.split(" ")[0];
            }

        } else if (expr instanceof SQLBinaryOpExpr) {
            if (StringUtils.isNotEmpty(((SQLBinaryOpExpr) expr).getLeft().toString())) {
                charset = ((SQLBinaryOpExpr) expr).getLeft().toString();
            }
        } else if (expr instanceof SQLCharExpr) {
            if (StringUtils.isNotEmpty(((SQLCharExpr) expr).getText())) {
                charset = ((SQLCharExpr) expr).getText();
            }
        }
        return charset;
    }

    private String getSqlName(SQLExpr sqlName) {
        if (sqlName == null) {
            return null;
        }

        if (sqlName instanceof SQLPropertyExpr) {
            SQLIdentifierExpr owner = (SQLIdentifierExpr) ((SQLPropertyExpr) sqlName).getOwner();
            return normalizeNoTrim(owner.getName()) + "."
                + normalizeNoTrim(((SQLPropertyExpr) sqlName).getName());
        } else if (sqlName instanceof SQLIdentifierExpr) {
            return normalizeNoTrim(((SQLIdentifierExpr) sqlName).getName());
        } else if (sqlName instanceof SQLCharExpr) {
            return ((SQLCharExpr) sqlName).getText();
        } else if (sqlName instanceof SQLMethodInvokeExpr) {
            return normalizeNoTrim(((SQLMethodInvokeExpr) sqlName).getMethodName());
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
        if (isInteger(dataTypStr)) {
            return dataTypStr;
        }
        if (!column.getDataType().getArguments().isEmpty()) {
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

    private boolean isInteger(String dataTypeStr) {
        return "tinyint".equalsIgnoreCase(dataTypeStr) || "smallint".equalsIgnoreCase(dataTypeStr)
            || "mediumint".equalsIgnoreCase(dataTypeStr) || "int".equalsIgnoreCase(dataTypeStr)
            || "integer".equalsIgnoreCase(dataTypeStr) || "bigint".equalsIgnoreCase(dataTypeStr);
    }

    private String convertDataTypeStr(SQLDataType dataType, String dataTypeStr) {
        boolean unsigned = false;
        if (dataType instanceof SQLDataTypeImpl) {
            //当使用zerofill 时，MySQL默认会自动加unsigned(无符号)属性
            SQLDataTypeImpl dataTypeImpl = (SQLDataTypeImpl) dataType;
            unsigned = dataTypeImpl.isUnsigned() || dataTypeImpl.isZerofill();
        }

        if ("decimal".equalsIgnoreCase(dataTypeStr) || "decimal(0)".equalsIgnoreCase(dataTypeStr)
            || "decimal(0,0)".equalsIgnoreCase(dataTypeStr)) {
            return "decimal(10,0)";

        } else if ("bit".equalsIgnoreCase(dataTypeStr)) {
            return "bit(1)";

        } else if ("boolean".equalsIgnoreCase(dataTypeStr) ||
            "bool".equalsIgnoreCase(dataTypeStr)) {
            return "tinyint";

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
        } else if (StringUtils.equalsIgnoreCase(dataTypeStr, "real")) {
            // If the REAL_AS_FLOAT SQL mode is enabled, REAL is a synonym for FLOAT rather than DOUBLE. this feature support @RebuildEventLogFilter
            return "double";
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
                            normalize(columnDefinition.getColumnName());
                        if (StringUtils.equalsIgnoreCase(columnName, _DRDS_IMPLICIT_ID_)) {
                            containsImplicitCol = true;
                        }
                    } else if (element instanceof MySqlPrimaryKey) {
                        MySqlPrimaryKey column = (MySqlPrimaryKey) element;
                        List<SQLSelectOrderByItem> pks = column.getColumns();
                        for (SQLSelectOrderByItem pk : pks) {
                            String columnName = normalize(getSqlName(pk.getExpr()));
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

}
