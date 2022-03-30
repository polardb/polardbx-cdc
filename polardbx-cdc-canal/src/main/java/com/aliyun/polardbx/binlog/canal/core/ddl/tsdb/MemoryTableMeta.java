/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
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
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLNotNullConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLNullConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlOrderingExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.alibaba.polardbx.druid.sql.repository.SchemaRepository;
import com.alibaba.polardbx.druid.util.FnvHash;
import com.alibaba.polardbx.druid.util.JdbcConstants;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于DDL维护的内存表结构
 *
 * @author agapple 2017年7月27日 下午4:19:40
 * @since 3.2.5
 */
public class MemoryTableMeta implements TableMetaTSDB {

    private final Map<List<String>, TableMeta> tableMetas = new ConcurrentHashMap<List<String>, TableMeta>();
    private SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
    private final Logger logger;

    public MemoryTableMeta(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean init(String destination) {
        return true;
    }

    @Override
    public void destory() {
        tableMetas.clear();
        repository = new SchemaRepository(JdbcConstants.MYSQL);
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        if (StringUtils.isBlank(ddl)) {
            return true;
        }

        tableMetas.clear();
        synchronized (this) {
            if (StringUtils.isNotEmpty(schema)) {
                repository.setDefaultSchema(schema);
            }

            try {
                // druid暂时flush privileges语法解析有问题
                if (!StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "flush")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "grant")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "create user")
                    && !StringUtils.startsWithIgnoreCase(StringUtils.trim(ddl), "drop user")) {
                    repository.console(ddl, FastSQLConstant.FEATURES);
                    tryRemoveSchema(position, ddl, schema);
                }
            } catch (Throwable e) {
                logger.warn("parse failed : " + ddl, e);
            }
        }

        return true;
    }

    @Override
    public TableMeta find(String schema, String table) {
        List<String> keys = Arrays.asList(schema, table);
        TableMeta tableMeta = tableMetas.get(keys);
        if (tableMeta == null) {
            synchronized (this) {
                tableMeta = tableMetas.get(keys);
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
        Schema schema = repository.findSchema(schemaName);
        SchemaObject schemaObject = schema.findTable(tableName);
        StringBuffer data = new StringBuffer(1024);
        schemaObject.getStatement().output(data);
        data.append(";");
        return data.toString();
    }

    private TableMeta parse(SQLCreateTableStatement statement) {
        int size = statement.getTableElementList().size();
        if (size > 0) {
            TableMeta tableMeta = new TableMeta();
            for (SQLAssignItem tableOption : statement.getTableOptions()) {
                if (tableOption instanceof SQLAssignItem) {
                    if (!(tableOption.getTarget() instanceof SQLIdentifierExpr)) {
                        continue;
                    }
                    String targetName = ((SQLIdentifierExpr) tableOption.getTarget()).getName();
                    if ("CHARACTER SET".equalsIgnoreCase(targetName) || "CHARSET".equalsIgnoreCase(targetName)) {
                        tableMeta.setCharset(((SQLIdentifierExpr) tableOption.getValue()).getName());
                        break;
                    }
                } else {
                    continue;
                }
            }
            for (int i = 0; i < size; ++i) {
                SQLTableElement element = statement.getTableElementList().get(i);
                processTableElement(element, tableMeta);
            }
            return tableMeta;
        }

        return null;
    }

    private void processTableElement(SQLTableElement element, TableMeta tableMeta) {
        if (element instanceof SQLColumnDefinition) {
            FieldMeta fieldMeta = new FieldMeta();
            SQLColumnDefinition column = (SQLColumnDefinition) element;
            String name = getSqlName(column.getName());
            // String charset = getSqlName(column.getCharsetExpr());
            SQLDataType dataType = column.getDataType();
            String dataTypStr = dataType.getName();
            if (dataType.getArguments().size() > 0) {
                dataTypStr += "(";
                for (int i = 0; i < column.getDataType().getArguments().size(); i++) {
                    if (i != 0) {
                        dataTypStr += ",";
                    }
                    SQLExpr arg = column.getDataType().getArguments().get(i);
                    dataTypStr += arg.toString();
                }
                dataTypStr += ")";
            }

            if (dataType instanceof SQLDataTypeImpl) {
                SQLDataTypeImpl dataTypeImpl = (SQLDataTypeImpl) dataType;
                if (dataTypeImpl.isUnsigned()) {
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
                fieldMeta.setDefaultValue(DruidDdlParser.unescapeQuotaName(getSqlName(column.getDefaultExpr())));
            }
            if (dataType instanceof SQLCharacterDataType) {
                final String charSetName = ((SQLCharacterDataType) dataType).getCharSetName();
                if (StringUtils.isNotEmpty(charSetName)) {
                    fieldMeta.setCharset(charSetName);
                } else {
                    final SQLCharExpr charsetExpr = (SQLCharExpr) ((SQLColumnDefinition) element).getCharsetExpr();
                    if (charsetExpr != null) {
                        fieldMeta.setCharset(charsetExpr.getText());
                    }
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
            return DruidDdlParser.unescapeName(owner.getName()) + "."
                + DruidDdlParser.unescapeName(((SQLPropertyExpr) sqlName).getName());
        } else if (sqlName instanceof SQLIdentifierExpr) {
            return DruidDdlParser.unescapeName(((SQLIdentifierExpr) sqlName).getName());
        } else if (sqlName instanceof SQLCharExpr) {
            return ((SQLCharExpr) sqlName).getText();
        } else if (sqlName instanceof SQLMethodInvokeExpr) {
            return DruidDdlParser.unescapeName(((SQLMethodInvokeExpr) sqlName).getMethodName());
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
                        result.add(SQLUtils.normalize(sqlConstraint.getName().getSimpleName()));
                    }
                }
            });
        }

        Collection<SchemaObject> objects = schemaRep.getIndexes();
        if (objects != null) {
            objects.forEach(o -> {
                if (o.getStatement() instanceof SQLCreateIndexStatement) {
                    SQLCreateIndexStatement createIndexStatement = (SQLCreateIndexStatement) o.getStatement();
                    String indexTable = SQLUtils.normalize(createIndexStatement.getTableName());
                    if (StringUtils.equalsIgnoreCase(indexTable, table)) {
                        SQLName sqlName = createIndexStatement.getIndexDefinition().getName();
                        if (sqlName != null) {
                            result.add(SQLUtils.normalize(sqlName.getSimpleName()));
                        }
                    }
                }
            });
        }

        return result;
    }

    private void tryRemoveSchema(BinlogPosition position, String ddlSql, String schema) throws IllegalAccessException {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddlSql, DbType.mysql, FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        if (stmt instanceof SQLDropDatabaseStatement) {
            Class<?> clazz = SchemaRepository.class;
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String name = field.getName();
                if ("schemas".equalsIgnoreCase(name)) {
                    field.setAccessible(true);
                    Map schemas = (Map) field.get(repository);
                    schemas.remove(schema);
                    logger.warn("schema is removed from schema repository, tso {}, schema {}, ddlSql {}.",
                        position == null ? "" : position.getRtso(), schema, ddlSql);
                    break;
                }
            }
            repository.setDefaultSchema((Schema) null);
        }
    }

    @Deprecated
    private void tryRemoveTable(BinlogPosition position, String ddlSql, String schema) throws IllegalAccessException {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddlSql, DbType.mysql, FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        // SchemaRepository的acceptDropTable、renameTable方法，在对带反引号的表进行处理时有问题，导致执行完drop sql后，table信息并未从objects中移除
        // 这里进行一下补偿操作，待bug修复后，可以移除，issue id：39638018
        if (stmt instanceof SQLDropTableStatement) {
            SQLDropTableStatement dropStmt = (SQLDropTableStatement) stmt;
            for (SQLExprTableSource tableSource : dropStmt.getTableSources()) {
                String table = tableSource.getTableName(true);
                doRemove(position == null ? "" : position.getRtso(), schema, table, ddlSql);
            }
        } else if (stmt instanceof MySqlRenameTableStatement) {
            MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) stmt;
            for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                String tableNameFrom = SQLUtils.normalize(item.getName().getSimpleName());
                doRemove(position == null ? "" : position.getRtso(), schema, tableNameFrom, ddlSql);
            }
        }
    }

    private void doRemove(String tso, String schema, String table, String ddlSql) throws IllegalAccessException {
        Schema schemaRep = repository.findSchema(schema);
        if (schemaRep == null) {
            return;
        }
        SchemaObject data = schemaRep.findTable(table);
        if (data == null) {
            return;
        }

        Class<?> clazz = schemaRep.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            if ("objects".equalsIgnoreCase(name)) {
                field.setAccessible(true);
                Map objects = (Map) field.get(schemaRep);
                long nameHashCode64 = FnvHash.hashCode64(table);
                objects.remove(nameHashCode64);
                logger.warn("table is removed from schema repository, tso {}, schema {},tableName {} ddl sql {}.",
                    tso, schema, table, ddlSql);
                break;
            }
        }

    }

    public SchemaRepository getRepository() {
        return repository;
    }

}
