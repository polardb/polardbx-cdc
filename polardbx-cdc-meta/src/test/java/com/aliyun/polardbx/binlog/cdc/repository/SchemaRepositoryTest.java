/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.repository;

import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.alibaba.polardbx.druid.sql.repository.SchemaRepository;
import com.alibaba.polardbx.druid.util.JdbcConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.SQLUtils.buildCreateLikeSql;

@Slf4j
public class SchemaRepositoryTest {

    @Test
    public void testAddIndexWithAlgorithmAndLock() {
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");
        repository.console("create table d1.t1 (id int,name varchar(16))");
        Schema schema = repository.findSchema("d1");
        Assert.assertNotNull(schema);
        SchemaObject table = schema.findTable("t1");
        Assert.assertNotNull(table);
        StringBuffer buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE t1 (\n"
            + "\tid int,\n"
            + "\tname varchar(16)\n"
            + ")", buffer.toString());

        // add index with ALGORITHM=INPLACE, LOCK=NONE
        repository.console("ALTER TABLE d1.t1 ALGORITHM=INPLACE, LOCK=NONE, ADD INDEX `idx_id`(id)");
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE t1 (\n"
            + "\tid int,\n"
            + "\tname varchar(16),\n"
            + "\tINDEX `idx_id`(id)\n"
            + ")", buffer.toString());

        // add same index again,  to test idempotent
        repository.console("ALTER TABLE d1.t1 ALGORITHM=INPLACE, LOCK=NONE, ADD INDEX `idx_id`(id)");
        buffer = new StringBuffer();
        if (table.getStatement() instanceof MySqlCreateTableStatement) {
            ((MySqlCreateTableStatement) table.getStatement()).normalizeTableOptions();
        }
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE t1 (\n"
            + "\tid int,\n"
            + "\tname varchar(16),\n"
            + "\tINDEX `idx_id`(id)\n"
            + ")", buffer.toString());
    }

    @Test
    public void testModifyColumnWithFirstAndAfter() {
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");

        // create table
        repository.console("create table `omc_modify_column_ordinal_test_tbllzj` (\n"
            + "\ta int primary key,\n"
            + "\tb int,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci");
        Schema schema = repository.findSchema("d1");
        Assert.assertNotNull(schema);
        SchemaObject table = schema.findTable("omc_modify_column_ordinal_test_tbllzj");
        Assert.assertNotNull(table);

        StringBuffer buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb int,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // modify column
        repository.console("/*+tddl:cmd_extra(omc_alter_table_with_gsi=true)*/"
            + "alter table omc_modify_column_ordinal_test_tbllzj modify column b bigint algorithm=omc");
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // modify column with first
        repository.console("/*+tddl:cmd_extra(omc_alter_table_with_gsi=true)*/"
            + "alter table omc_modify_column_ordinal_test_tbllzj modify column c bigint first algorithm=omc");
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj` (\n"
            + "\tc bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb bigint,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // modify column with after
        repository.console("/*+tddl:cmd_extra(omc_alter_table_with_gsi=true)*/"
            + "alter table omc_modify_column_ordinal_test_tbllzj modify column d bigint after e algorithm=omc");
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj` (\n"
            + "\tc bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb bigint,\n"
            + "\te int,\n"
            + "\td bigint\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());
    }

    @Test
    public void testAlterTableWithColumnComposite() {
        // create table
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");
        repository.console("\n"
            + "create table `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int primary key,\n"
            + "\tb int,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci");
        Schema schema = repository.findSchema("d1");
        SchemaObject table = schema.findTable("omc_modify_column_ordinal_test_tbllzj_kvci_00000");
        StringBuffer buffer = new StringBuffer();
        table.getStatement().output(buffer);

        //add column with after
        String ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tadd column `b_rgek` bigint after `b`,\n"
            + "\talgorithm = default";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb int,\n"
            + "\t`b_rgek` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // add column with generated
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tadd column `b_checker_abgm` bigint generated always as (alter_type(`b`)) virtual,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb int,\n"
            + "\t`b_rgek` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int,\n"
            + "\t`b_checker_abgm` bigint GENERATED ALWAYS AS (alter_type(`b`)) VIRTUAL\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // drop clolumn
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tdrop column `b_checker_abgm`,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\tb int,\n"
            + "\t`b_rgek` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // exchange column
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tchange column `b` `b_rgek` int(11) default null,\n"
            + "\tchange column `b_rgek` `b` bigint,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b_rgek` int(11) DEFAULT NULL,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // drop column
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tdrop column `b_rgek`,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // add column with first
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tadd column `c_dmrs` bigint first,\n"
            + "\talgorithm = default";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c_dmrs` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // add column with virtual
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tadd column `c_checker_ofcg` bigint generated always as (alter_type(`c`)) virtual,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c_dmrs` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int,\n"
            + "\t`c_checker_ofcg` bigint GENERATED ALWAYS AS (alter_type(`c`)) VIRTUAL\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // drop column
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tdrop column `c_checker_ofcg`,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c_dmrs` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // exchange column
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tchange column `c` `c_dmrs` int(11) default null,\n"
            + "\tchange column `c_dmrs` `c` bigint,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\t`c_dmrs` int(11) DEFAULT NULL,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        // drop column
        ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tdrop column `c_dmrs`,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());
    }

    @Test
    public void testExchangeColumnName() {
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");
        repository.console(
            "CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
                + "\t`c_dmrs` bigint,\n"
                + "\ta int PRIMARY KEY,\n"
                + "\t`b` bigint,\n"
                + "\tc int,\n"
                + "\td int,\n"
                + "\te int\n"
                + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci");

        Schema schema = repository.findSchema("d1");
        SchemaObject table = schema.findTable("omc_modify_column_ordinal_test_tbllzj_kvci_00000");
        StringBuffer buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c_dmrs` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\tc int,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());

        String ddl = "alter table `omc_modify_column_ordinal_test_tbllzj_kvci_00000`\n"
            + "\tchange column `c` `c_dmrs` int(11) default null,\n"
            + "\tchange column `c_dmrs` `c` bigint,\n"
            + "\talgorithm = inplace";
        repository.console(ddl);
        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        Assert.assertEquals("CREATE TABLE `omc_modify_column_ordinal_test_tbllzj_kvci_00000` (\n"
            + "\t`c` bigint,\n"
            + "\ta int PRIMARY KEY,\n"
            + "\t`b` bigint,\n"
            + "\t`c_dmrs` int(11) DEFAULT NULL,\n"
            + "\td int,\n"
            + "\te int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", buffer.toString());
    }

    //see: https://aone.alibaba-inc.com/v2/project/860366/bug/52881689
    @Test
    public void testCreateTableLike() {
        String schema = "d`b1";
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema(schema);

        String t1 = "`gsi-`test_table`";
        String t2 = "`gsi-`test_table`_0h4o_009";
        String t3 = "`gsi-`test_table`_0h4o_110";
        String createSql = String.format("create table %s (id bigint primary key)", "`" + escape(t1) + "`");
        String createLikeSql = buildCreateLikeSql(t2, schema, t1);

        repository.console(createSql);
        SchemaObject schemaObject = findTable(repository, t1);
        Assert.assertNotNull(schemaObject);
        SQLCreateTableStatement createTableStatement = (SQLCreateTableStatement) schemaObject.getStatement();
        Assert.assertFalse(createTableStatement.getTableElementList().isEmpty());

        // with schema
        repository.console(createLikeSql);
        schemaObject = findTable(repository, t2);
        Assert.assertNotNull(schemaObject);
        createTableStatement = (SQLCreateTableStatement) schemaObject.getStatement();
        Assert.assertFalse(createTableStatement.getTableElementList().isEmpty());

        // without schema
        createLikeSql = "create table ```gsi-``test_table``_0h4o_110` like ```gsi-``test_table```";
        repository.console(createLikeSql);
        schemaObject = findTable(repository, t3);
        Assert.assertNotNull(schemaObject);
        createTableStatement = (SQLCreateTableStatement) schemaObject.getStatement();
        Assert.assertFalse(createTableStatement.getTableElementList().isEmpty());
    }

    private SchemaObject findTable(SchemaRepository repository, String tableName) {
        Schema schema = repository.findSchema("d`b1");
        return schema.findTable(tableName);
    }
}
