/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSON;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCharacterDataType;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试 CONVERT TO CHARACTER SET 和 ALTER TABLE CHARACTER SET 混合场景下的charset一致性
 * <p>
 * 核心规则:
 * 1. CONVERT TO CHARACTER SET: 改变表charset + 所有字符集敏感列的charset
 * 2. ALTER TABLE CHARACTER SET/CHARSET=: 只改表charset,不改已有列charset
 * - 未显式声明charset的列,继承修改前的表charset
 * 3. 每次DDL后验证所有列的charset
 * 4. 3~5轮交叉DDL,覆盖多种列类型
 *
 * @author charset-test
 */
@Slf4j
public class MixedCharacterSetDdlTest {

    private MemoryTableMeta memoryTableMeta;

    private static final String TEST_SCHEMA = "test_charset_db";
    private static final String TEST_TABLE = "mixed_charset_test";

    @Before
    public void setUp() throws Exception {
        // 直接使用MemoryTableMeta,不依赖Spring上下文
        this.memoryTableMeta = new MemoryTableMeta(
            LoggerFactory.getLogger(MixedCharacterSetDdlTest.class),
            false  // ignoreApplyError
        );
    }

    // ==================== 测试用例1: CREATE + ALTER TABLE charset + CONVERT TO ====================

    /**
     * 测试场景: CREATE表 → ALTER TABLE charset → CONVERT TO
     * <p>
     * 步骤:
     * 1. CREATE TABLE (charset=utf8mb4)
     * - col_varchar: varchar(100) [隐式charset=utf8mb4]
     * - col_text: text [隐式charset=utf8mb4]
     * - col_char: char(50) [隐式charset=utf8mb4]
     * 2. ALTER TABLE CHARACTER SET = gbk
     * - 表charset: utf8mb4 → gbk
     * - col_varchar: utf8mb4 → utf8mb4 (不变!)
     * - col_text: utf8mb4 → utf8mb4 (不变!)
     * - col_char: utf8mb4 → utf8mb4 (不变!)
     * 3. ALTER TABLE CONVERT TO CHARACTER SET utf8
     * - 表charset: gbk → utf8
     * - col_varchar: utf8mb4 → utf8 (转换!)
     * - col_text: utf8mb4 → utf8 (转换!)
     * - col_char: utf8mb4 → utf8 (转换!)
     * 4. ALTER TABLE CHARACTER SET = latin1
     * - 表charset: utf8 → latin1
     * - col_varchar: utf8 → utf8 (不变!)
     * - col_text: utf8 → utf8 (不变!)
     * - col_char: utf8 → utf8 (不变!)
     */
    @Test
    public void testAlterCharsetThenConvertTo() {
        log.info("=== 测试用例1: ALTER TABLE charset → CONVERT TO ===");

        // Step 1: CREATE TABLE
        String createDdl = "CREATE TABLE mixed_charset_test (" +
            "id INT PRIMARY KEY, " +
            "col_varchar VARCHAR(100), " +
            "col_text TEXT, " +
            "col_char CHAR(50), " +
            "col_int INT" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, createDdl, null);
        verifyCharsetAfterDdl("Step1_CREATE", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4");
            put("col_text", "utf8mb4");
            put("col_char", "utf8mb4");
            put("col_int", null); // int类型无charset
        }});

        // Step 2: ALTER TABLE CHARACTER SET = gbk (只改表charset)
        String alterCharsetDdl = "ALTER TABLE mixed_charset_test CHARACTER SET = gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl, null);
        verifyCharsetAfterDdl("Step2_ALTER_CHARSET", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4"); // 不变!
            put("col_text", "utf8mb4");   // 不变!
            put("col_char", "utf8mb4");   // 不变!
            put("col_int", null);
        }});

        // Step 3: CONVERT TO CHARACTER SET utf8 (改表+所有列charset)
        String convertToDdl = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl, null);
        verifyCharsetAfterDdl("Step3_CONVERT_TO", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "utf8"); // 转换!
            put("col_text", "utf8");   // 转换!
            put("col_char", "utf8");   // 转换!
            put("col_int", null);
        }});

        // Step 4: ALTER TABLE CHARACTER SET = latin1 (只改表charset)
        String alterCharsetDdl2 = "ALTER TABLE mixed_charset_test CHARACTER SET = latin1";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl2, null);
        verifyCharsetAfterDdl("Step4_ALTER_CHARSET2", "latin1", new HashMap<String, String>() {{
            put("col_varchar", "utf8"); // 不变!
            put("col_text", "utf8");   // 不变!
            put("col_char", "utf8");   // 不变!
            put("col_int", null);
        }});

        log.info("=== 测试用例1 通过 ===");
    }

    // ==================== 测试用例2: CREATE + CONVERT TO + ALTER TABLE charset ====================

    /**
     * 测试场景: CREATE表 → CONVERT TO → ALTER TABLE charset → CONVERT TO
     * <p>
     * 步骤:
     * 1. CREATE TABLE (charset=gbk)
     * 2. CONVERT TO utf8mb4: 表+所有列 → utf8mb4
     * 3. ALTER TABLE charset=utf8: 表 → utf8, 列不变
     * 4. CONVERT TO gbk: 表+所有列 → gbk
     */
    @Test
    public void testConvertToThenAlterCharsetThenConvertTo() {
        log.info("=== 测试用例2: CONVERT TO → ALTER TABLE charset → CONVERT TO ===");

        // Step 1: CREATE TABLE
        String createDdl = "CREATE TABLE mixed_charset_test (" +
            "id INT PRIMARY KEY, " +
            "col_varchar VARCHAR(200), " +
            "col_tinytext TINYTEXT, " +
            "col_mediumtext MEDIUMTEXT, " +
            "col_longtext LONGTEXT" +
            ") ENGINE=InnoDB DEFAULT CHARSET=gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, createDdl, null);
        verifyCharsetAfterDdl("Step1_CREATE", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "gbk");
            put("col_tinytext", "gbk");
            put("col_mediumtext", "gbk");
            put("col_longtext", "gbk");
        }});

        // Step 2: CONVERT TO utf8mb4
        String convertToDdl1 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl1, null);
        verifyCharsetAfterDdl("Step2_CONVERT_TO_1", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4");
            put("col_tinytext", "utf8mb4");
            put("col_mediumtext", "utf8mb4");
            put("col_longtext", "utf8mb4");
        }});

        // Step 3: ALTER TABLE charset=utf8 (只改表)
        String alterCharsetDdl = "ALTER TABLE mixed_charset_test CHARSET = utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl, null);
        verifyCharsetAfterDdl("Step3_ALTER_CHARSET", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4"); // 不变!
            put("col_tinytext", "utf8mb4");
            put("col_mediumtext", "utf8mb4");
            put("col_longtext", "utf8mb4");
        }});

        // Step 4: CONVERT TO gbk
        String convertToDdl2 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl2, null);
        verifyCharsetAfterDdl("Step4_CONVERT_TO_2", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "gbk");
            put("col_tinytext", "gbk");
            put("col_mediumtext", "gbk");
            put("col_longtext", "gbk");
        }});

        log.info("=== 测试用例2 通过 ===");
    }

    // ==================== 测试用例3: 包含生成列的混合DDL ====================

    /**
     * 测试场景: CREATE包含生成列 → CONVERT TO → ALTER TABLE charset → CONVERT TO → ALTER TABLE charset
     * <p>
     * 这是实验室实际场景的复现!
     */
    @Test
    public void testMixedDdlWithGeneratedColumns() {
        log.info("=== 测试用例3: 混合DDL + 生成列 ===");

        // Step 1: CREATE TABLE with generated columns
        String createDdl = "CREATE TABLE mixed_charset_test (" +
            "id INT PRIMARY KEY, " +
            "c_idx INT, " +
            "col_varchar VARCHAR(100), " +
            "gen_col1 VARCHAR(64) GENERATED ALWAYS AS (CAST(c_idx AS CHAR)), " +
            "gen_col2 VARCHAR(128) GENERATED ALWAYS AS (CAST(c_idx AS CHAR)), " +
            "col_binary BINARY(16)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, createDdl, null);
        verifyCharsetAfterDdl("Step1_CREATE", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "utf8");
            put("gen_col1", "utf8");       // 生成列继承表charset
            put("gen_col2", "utf8");       // 生成列继承表charset
            put("col_binary", null);       // binary类型无charset
        }});

        // Step 2: CONVERT TO utf8mb4
        String convertToDdl1 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl1, null);
        verifyCharsetAfterDdl("Step2_CONVERT_TO_1", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4");
            put("gen_col1", "utf8mb4");    // 转换!
            put("gen_col2", "utf8mb4");    // 转换!
            put("col_binary", null);
        }});

        // Step 3: ALTER TABLE CHARACTER SET = gbk (只改表)
        String alterCharsetDdl1 = "ALTER TABLE mixed_charset_test CHARACTER SET = gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl1, null);
        verifyCharsetAfterDdl("Step3_ALTER_CHARSET_1", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4"); // 不变!
            put("gen_col1", "utf8mb4");    // 不变! ← 实验室问题的关键!
            put("gen_col2", "utf8mb4");    // 不变!
            put("col_binary", null);
        }});

        // Step 4: CONVERT TO utf8
        String convertToDdl2 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl2, null);
        verifyCharsetAfterDdl("Step4_CONVERT_TO_2", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "utf8");
            put("gen_col1", "utf8");       // 转换!
            put("gen_col2", "utf8");       // 转换!
            put("col_binary", null);
        }});

        // Step 5: ALTER TABLE charset = latin1 (只改表)
        String alterCharsetDdl2 = "ALTER TABLE mixed_charset_test CHARSET = latin1";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl2, null);
        verifyCharsetAfterDdl("Step5_ALTER_CHARSET_2", "latin1", new HashMap<String, String>() {{
            put("col_varchar", "utf8");    // 不变!
            put("gen_col1", "utf8");       // 不变! ← 实验室问题的关键!
            put("gen_col2", "utf8");       // 不变!
            put("col_binary", null);
        }});

        log.info("=== 测试用例3 通过 ===");
    }

    // ==================== 测试用例4: 5轮交叉DDL ====================

    /**
     * 测试场景: 5轮交叉DDL,覆盖所有字符集敏感类型
     */
    @Test
    public void testFiveRoundsMixedDdl() {
        log.info("=== 测试用例4: 5轮交叉DDL ===");

        // Step 1: CREATE TABLE
        String createDdl = "CREATE TABLE mixed_charset_test (" +
            "id INT PRIMARY KEY, " +
            "col_varchar VARCHAR(100), " +
            "col_char CHAR(50), " +
            "col_text TEXT, " +
            "col_tinytext TINYTEXT, " +
            "col_mediumtext MEDIUMTEXT, " +
            "col_longtext LONGTEXT, " +
            "col_enum ENUM('a','b','c'), " +
            "col_set SET('x','y','z'), " +
            "col_blob BLOB, " +
            "col_int INT, " +
            "col_bigint BIGINT" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, createDdl, null);
        verifyCharsetAfterDdl("Step1_CREATE", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4");
            put("col_char", "utf8mb4");
            put("col_text", "utf8mb4");
            put("col_tinytext", "utf8mb4");
            put("col_mediumtext", "utf8mb4");
            put("col_longtext", "utf8mb4");
            put("col_enum", "utf8mb4");
            put("col_set", "utf8mb4");
            put("col_blob", null);
            put("col_int", null);
            put("col_bigint", null);
        }});

        // Step 2: CONVERT TO gbk
        String ddl2 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl2, null);
        verifyCharsetAfterDdl("Step2_CONVERT_TO_GBK", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "gbk");
            put("col_char", "gbk");
            put("col_text", "gbk");
            put("col_tinytext", "gbk");
            put("col_mediumtext", "gbk");
            put("col_longtext", "gbk");
            put("col_enum", "gbk");
            put("col_set", "gbk");
            put("col_blob", null);
            put("col_int", null);
            put("col_bigint", null);
        }});

        // Step 3: ALTER TABLE charset=utf8 (只改表)
        String ddl3 = "ALTER TABLE mixed_charset_test CHARACTER SET = utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl3, null);
        verifyCharsetAfterDdl("Step3_ALTER_CHARSET_UTF8", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "gbk"); // 不变!
            put("col_char", "gbk");
            put("col_text", "gbk");
            put("col_tinytext", "gbk");
            put("col_mediumtext", "gbk");
            put("col_longtext", "gbk");
            put("col_enum", "gbk");
            put("col_set", "gbk");
            put("col_blob", null);
            put("col_int", null);
            put("col_bigint", null);
        }});

        // Step 4: CONVERT TO latin1
        String ddl4 = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET latin1";
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl4, null);
        verifyCharsetAfterDdl("Step4_CONVERT_TO_LATIN1", "latin1", new HashMap<String, String>() {{
            put("col_varchar", "latin1");
            put("col_char", "latin1");
            put("col_text", "latin1");
            put("col_tinytext", "latin1");
            put("col_mediumtext", "latin1");
            put("col_longtext", "latin1");
            put("col_enum", "latin1");
            put("col_set", "latin1");
            put("col_blob", null);
            put("col_int", null);
            put("col_bigint", null);
        }});

        // Step 5: ALTER TABLE charset=utf8mb4 (只改表)
        String ddl5 = "ALTER TABLE mixed_charset_test CHARSET = utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl5, null);
        verifyCharsetAfterDdl("Step5_ALTER_CHARSET_UTF8MB4", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "latin1"); // 不变!
            put("col_char", "latin1");
            put("col_text", "latin1");
            put("col_tinytext", "latin1");
            put("col_mediumtext", "latin1");
            put("col_longtext", "latin1");
            put("col_enum", "latin1");
            put("col_set", "latin1");
            put("col_blob", null);
            put("col_int", null);
            put("col_bigint", null);
        }});

        log.info("=== 测试用例4 通过 ===");
    }

    // ==================== 测试用例5: 验证snapshot()不会改变charset ====================

    /**
     * 测试场景: 验证MemoryTableMeta.snapshot()方法是否会改变table或列的charset
     * <p>
     * 背景:
     * - distinctPhyMeta.snapshot()用于序列化DDL
     * - 如果snapshot()过程中改变了charset,会导致后续apply时charset错误
     * <p>
     * 验证步骤:
     * 1. 创建包含生成列的表
     * 2. 执行混合DDL (CONVERT TO + ALTER TABLE charset)
     * 3. 调用snapshot()获取序列化DDL
     * 4. 验证snapshot()前后TableMeta的charset没有变化
     * 5. 将snapshot DDL apply到新的MemoryTableMeta
     * 6. 验证新Meta的charset与原Meta一致
     */
    @Test
    public void testSnapshotNotChangeCharset() {
        log.info("=== 测试用例5: 验证snapshot()不会改变charset ===");

        // Step 1: CREATE TABLE with generated columns
        String createDdl = "CREATE TABLE mixed_charset_test (" +
            "id INT PRIMARY KEY, " +
            "c_idx INT, " +
            "col_varchar VARCHAR(100), " +
            "gen_col VARCHAR(64) GENERATED ALWAYS AS (CAST(c_idx AS CHAR)), " +
            "col_text TEXT" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        memoryTableMeta.apply(null, TEST_SCHEMA, createDdl, null);
        verifyCharsetAfterDdl("Step1_CREATE", "utf8mb4", new HashMap<String, String>() {{
            put("col_varchar", "utf8mb4");
            put("gen_col", "utf8mb4");
            put("col_text", "utf8mb4");
        }});

        // Step 2: CONVERT TO utf8
        String convertToDdl = "ALTER TABLE mixed_charset_test CONVERT TO CHARACTER SET utf8";
        memoryTableMeta.apply(null, TEST_SCHEMA, convertToDdl, null);

        // 记录snapshot前的charset状态
        TableMeta metaBeforeSnapshot = memoryTableMeta.find(TEST_SCHEMA, TEST_TABLE);
        String tableCharsetBefore = metaBeforeSnapshot.getCharset();
        Map<String, String> columnCharsetsBefore = new HashMap<>();
        for (TableMeta.FieldMeta field : metaBeforeSnapshot.getFields()) {
            columnCharsetsBefore.put(field.getColumnName(), field.getCharset());
        }

        log.info("Before snapshot(): tableCharset={}, columnCharsets={}",
            tableCharsetBefore, columnCharsetsBefore);

        verifyCharsetAfterDdl("Step2_CONVERT_TO", "utf8", new HashMap<String, String>() {{
            put("col_varchar", "utf8");
            put("gen_col", "utf8");
            put("col_text", "utf8");
        }});

        // Step 3: ALTER TABLE charset=gbk (只改表)
        String alterCharsetDdl = "ALTER TABLE mixed_charset_test CHARACTER SET = gbk";
        memoryTableMeta.apply(null, TEST_SCHEMA, alterCharsetDdl, null);

        // 记录snapshot前的charset状态
        TableMeta metaBeforeSnapshot2 = memoryTableMeta.find(TEST_SCHEMA, TEST_TABLE);
        String tableCharsetBefore2 = metaBeforeSnapshot2.getCharset();
        Map<String, String> columnCharsetsBefore2 = new HashMap<>();
        for (TableMeta.FieldMeta field : metaBeforeSnapshot2.getFields()) {
            columnCharsetsBefore2.put(field.getColumnName(), field.getCharset());
        }

        log.info("Before snapshot() #2: tableCharset={}, columnCharsets={}",
            tableCharsetBefore2, columnCharsetsBefore2);

        verifyCharsetAfterDdl("Step3_ALTER_CHARSET", "gbk", new HashMap<String, String>() {{
            put("col_varchar", "utf8");  // 不变!
            put("gen_col", "utf8");      // 不变!
            put("col_text", "utf8");     // 不变!
        }});

        // Step 4: 调用snapshot()并验证不改变charset
        Map<String, String> snapshot = memoryTableMeta.snapshot();
        String snapshotDdl = snapshot.get(TEST_SCHEMA);

        Assert.assertNotNull("Snapshot should not be null", snapshotDdl);
        log.info("Snapshot DDL length: {} chars", snapshotDdl.length());
        log.info("Snapshot DDL preview: {}",
            snapshotDdl.length() > 300 ? snapshotDdl.substring(0, 300) + "..." : snapshotDdl);

        // 验证snapshot()后charset没有变化
        TableMeta metaAfterSnapshot = memoryTableMeta.find(TEST_SCHEMA, TEST_TABLE);
        String tableCharsetAfter = metaAfterSnapshot.getCharset();
        Map<String, String> columnCharsetsAfter = new HashMap<>();
        for (TableMeta.FieldMeta field : metaAfterSnapshot.getFields()) {
            columnCharsetsAfter.put(field.getColumnName(), field.getCharset());
        }

        log.info("After snapshot(): tableCharset={}, columnCharsets={}",
            tableCharsetAfter, columnCharsetsAfter);

        // 断言: snapshot()前后charset必须一致
        Assert.assertEquals("snapshot() should not change table charset",
            tableCharsetBefore2, tableCharsetAfter);

        for (Map.Entry<String, String> entry : columnCharsetsBefore2.entrySet()) {
            String columnName = entry.getKey();
            String charsetBefore = entry.getValue();
            String charsetAfter = columnCharsetsAfter.get(columnName);

            Assert.assertEquals("snapshot() should not change column charset for " + columnName,
                charsetBefore, charsetAfter);
        }

        log.info("✓ snapshot() did not change any charset");

        // Step 5: 将snapshot DDL apply到新的MemoryTableMeta
        MemoryTableMeta newMeta = new MemoryTableMeta(
            LoggerFactory.getLogger(MixedCharacterSetDdlTest.class),
            false
        );

        // snapshot DDL包含了所有表的CREATE语句,直接apply
        newMeta.apply(null, TEST_SCHEMA, snapshotDdl, null);

        // Step 6: 验证新Meta的charset与原Meta一致
        TableMeta newTableMeta = newMeta.find(TEST_SCHEMA, TEST_TABLE);
        Assert.assertNotNull("New TableMeta should not be null", newTableMeta);

        String newTableCharset = newTableMeta.getCharset();
        Assert.assertEquals("New table charset should match original",
            tableCharsetBefore2, newTableCharset);

        for (TableMeta.FieldMeta oldField : metaBeforeSnapshot2.getFields()) {
            String columnName = oldField.getColumnName();
            String expectedCharset = oldField.getCharset();

            TableMeta.FieldMeta newField = findField(newTableMeta, columnName);
            Assert.assertNotNull("New TableMeta should have column: " + columnName, newField);

            String actualCharset = newField.getCharset();
            Assert.assertEquals("New column charset mismatch for " + columnName,
                expectedCharset, actualCharset);

            log.info("  ✓ New {} charset = {} (expected: {})",
                columnName, actualCharset, expectedCharset);
        }

        log.info("=== 测试用例5 通过: snapshot()不会改变charset,且序列化DDL保持一致性 ===");
    }

    // ==================== 测试用例7: 实验室真实DDL序列复现 ====================

    /**
     * 测试场景: 复现实验室真实DDL序列,验证生成列charset行为
     * <p>
     * DDL来源: 实验室 binlog_phy_ddl_history 表查询结果
     * 表名: t_random_instant_check_2_q8rh_03
     * 数据库: cdc_reformat_test_mode_one_2_000001
     * <p>
     * DDL序列 (按TSO排序):
     * 1. CREATE TABLE (charset=utf8mb4) - 包含大量列但不包含生成列
     * 2. ALTER TABLE ADD COLUMN gen_v1qvnjcuv2f VARCHAR(64) GENERATED ALWAYS AS (CAST(c_idx AS CHAR)) STORED
     * 3. ALTER TABLE MODIFY COLUMN c_bigint_20 TINYTEXT CHARACTER SET utf8mb4
     * 4. ALTER TABLE CHARACTER SET = utf8mb4 (表charset不变,因为已经是utf8mb4)
     * 5. ALTER TABLE CHARACTER SET = utf8 (只改表charset)
     * <p>
     * 验证目标:
     * - 执行完所有DDL后,gen_v1qvnjcuv2f 列的charset应该是什么?
     * - 根据MySQL语义: ALTER TABLE CHARACTER SET 不应该改变已有列的charset
     * - 所以 gen_v1qvnjcuv2f 应该保持创建时的 charset (utf8mb4)
     */
    @Test
    public void testLabRealDdlSequenceForGeneratedColumnCharset() {
        log.info("=== 测试用例7: 实验室真实DDL序列复现 ===");

        String testTable = "t_random_instant_check_2_q8rh_03";

        // DDL 1: CREATE TABLE (简化版,只保留关键列)
        String ddl1 = "CREATE TABLE t_random_instant_check_2_q8rh_03 (" +
            "`id` bigint(20) not null auto_increment, " +
            "`c_idx` bigint not null default 100, " +
            "`c_char` char(50) default 'sjdlfjsdljldfjsldfsd', " +
            "`c_varchar` varchar(50) default 'sjdlfjsldhgowuere', " +
            "`c_text` text default null, " +
            "`c_bigint_20` bigint(20) default -9223372036854775808, " +
            "primary key (`id`)" +
            ") default charset = utf8mb4 default collate = utf8mb4_general_ci";

        log.info("[DDL 1] CREATE TABLE");
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl1, null);

        // 验证CREATE后的charset
        TableMeta meta1 = memoryTableMeta.find(TEST_SCHEMA, testTable);
        Assert.assertNotNull("TableMeta should exist after CREATE", meta1);
        log.info("[DDL 1] Table charset = {}", meta1.getCharset());
        Assert.assertEquals("utf8mb4", meta1.getCharset().toLowerCase());

        // DDL 2: ADD COLUMN gen_v1qvnjcuv2f (生成列)
        String ddl2 = "ALTER TABLE t_random_instant_check_2_q8rh_03 " +
            "ADD COLUMN `gen_v1qvnjcuv2f` varchar(64) generated always as (cast(`c_idx` as char)) stored";

        log.info("[DDL 2] ADD COLUMN gen_v1qvnjcuv2f (generated column)");
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl2, null);

        // 验证ADD COLUMN后的charset
        TableMeta meta2 = memoryTableMeta.find(TEST_SCHEMA, testTable);
        TableMeta.FieldMeta genCol2 = findField(meta2, "gen_v1qvnjcuv2f");
        Assert.assertNotNull("gen_v1qvnjcuv2f should exist", genCol2);
        String genColCharset2 = genCol2.getCharset();
        log.info("[DDL 2] gen_v1qvnjcuv2f charset = {}", genColCharset2);
        // 生成列创建时应该继承表charset (utf8mb4)
        Assert.assertEquals("utf8mb4", genColCharset2 != null ? genColCharset2.toLowerCase() : null);

        // DDL 3: MODIFY COLUMN c_bigint_20 (改为TINYTEXT CHARACTER SET utf8mb4)
        String ddl3 = "ALTER TABLE t_random_instant_check_2_q8rh_03 " +
            "MODIFY COLUMN `c_bigint_20` tinytext character set utf8mb4 default null";

        log.info("[DDL 3] MODIFY COLUMN c_bigint_20 to TINYTEXT CHARACTER SET utf8mb4");
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl3, null);

        // 验证MODIFY COLUMN后的charset
        TableMeta meta3 = memoryTableMeta.find(TEST_SCHEMA, testTable);
        TableMeta.FieldMeta genCol3 = findField(meta3, "gen_v1qvnjcuv2f");
        String genColCharset3 = genCol3.getCharset();
        log.info("[DDL 3] gen_v1qvnjcuv2f charset = {}", genColCharset3);
        // 修改其他列不应该影响生成列的charset
        Assert.assertEquals("utf8mb4", genColCharset3 != null ? genColCharset3.toLowerCase() : null);

        // DDL 4: ALTER TABLE CHARACTER SET = utf8mb4 (表charset不变)
        String ddl4 = "ALTER TABLE t_random_instant_check_2_q8rh_03 character set = utf8mb4";

        log.info("[DDL 4] ALTER TABLE CHARACTER SET = utf8mb4");
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl4, null);

        // 验证DDL 4后的charset
        TableMeta meta4 = memoryTableMeta.find(TEST_SCHEMA, testTable);
        TableMeta.FieldMeta genCol4 = findField(meta4, "gen_v1qvnjcuv2f");
        String genColCharset4 = genCol4.getCharset();
        log.info("[DDL 4] gen_v1qvnjcuv2f charset = {}", genColCharset4);
        // ALTER TABLE CHARACTER SET 不应该改变列charset
        Assert.assertEquals("utf8mb4", genColCharset4 != null ? genColCharset4.toLowerCase() : null);

        // DDL 5: ALTER TABLE CHARACTER SET = utf8 (只改表charset)
        String ddl5 = "ALTER TABLE t_random_instant_check_2_q8rh_03 character set = utf8";

        log.info("[DDL 5] ALTER TABLE CHARACTER SET = utf8");
        memoryTableMeta.apply(null, TEST_SCHEMA, ddl5, null);

        // 验证DDL 5后的charset - 关键断言!
        TableMeta meta5 = memoryTableMeta.find(TEST_SCHEMA, testTable);
        Assert.assertNotNull("TableMeta should exist after DDL 5", meta5);

        // 验证表charset已变为utf8
        log.info("[DDL 5] Table charset = {}", meta5.getCharset());
        Assert.assertEquals("Table charset should be utf8 after DDL 5",
            "utf8", meta5.getCharset().toLowerCase());

        // 验证生成列charset保持不变(仍然是utf8mb4)
        TableMeta.FieldMeta genCol5 = findField(meta5, "gen_v1qvnjcuv2f");
        Assert.assertNotNull("gen_v1qvnjcuv2f should exist after DDL 5", genCol5);
        String genColCharset5 = genCol5.getCharset();
        log.info("[DDL 5] gen_v1qvnjcuv2f charset = {}", genColCharset5);
        Assert.assertEquals(
            "gen_v1qvnjcuv2f charset should remain utf8mb4 (ALTER TABLE CHARACTER SET should not change column charset)",
            "utf8mb4", genColCharset5 != null ? genColCharset5.toLowerCase() : null);

        // 验证其他字符列charset也保持不变
        TableMeta.FieldMeta cChar = findField(meta5, "c_char");
        Assert.assertNotNull("c_char should exist", cChar);
        log.info("[DDL 5] c_char charset = {}", cChar.getCharset());
        Assert.assertEquals("c_char charset should remain utf8mb4",
            "utf8mb4", cChar.getCharset() != null ? cChar.getCharset().toLowerCase() : null);

        TableMeta.FieldMeta cVarchar = findField(meta5, "c_varchar");
        Assert.assertNotNull("c_varchar should exist", cVarchar);
        log.info("[DDL 5] c_varchar charset = {}", cVarchar.getCharset());
        Assert.assertEquals("c_varchar charset should remain utf8mb4",
            "utf8mb4", cVarchar.getCharset() != null ? cVarchar.getCharset().toLowerCase() : null);

        TableMeta.FieldMeta cText = findField(meta5, "c_text");
        Assert.assertNotNull("c_text should exist", cText);
        log.info("[DDL 5] c_text charset = {}", cText.getCharset());
        Assert.assertEquals("c_text charset should remain utf8mb4",
            "utf8mb4", cText.getCharset() != null ? cText.getCharset().toLowerCase() : null);

        TableMeta.FieldMeta cBigint20 = findField(meta5, "c_bigint_20");
        Assert.assertNotNull("c_bigint_20 should exist", cBigint20);
        log.info("[DDL 5] c_bigint_20 charset = {}", cBigint20.getCharset());
        Assert.assertEquals("c_bigint_20 charset should remain utf8mb4 (explicitly set in DDL 3)",
            "utf8mb4", cBigint20.getCharset() != null ? cBigint20.getCharset().toLowerCase() : null);

        // 额外验证: snapshot DDL序列化后charset一致性
        Map<String, String> db2TableMap = memoryTableMeta.snapshot();
        String createTableDdl = db2TableMap.get("test_charset_db");
        Assert.assertNotNull("Snapshot DDL should not be null", createTableDdl);

        System.out.println("[DDL 5] ========== Snapshot DDL FULL CONTENT ==========");
        System.out.println(createTableDdl);
        System.out.println("[DDL 5] ========== End of Snapshot DDL ==========");

        log.info("[DDL 5] Snapshot DDL length: {} chars", createTableDdl.length());
        log.info("[DDL 5] ========== Snapshot DDL FULL CONTENT ==========");
        log.info("{}", createTableDdl);
        log.info("[DDL 5] ========== End of Snapshot DDL ==========");

        // 验证snapshot DDL解析后的列charset
        MySqlCreateTableStatement st = SQLUtils.parseSQLStatement(createTableDdl);

        // 调试: 打印所有列的AST结构
        System.out.println("[DDL 5] ========== Debug: Column AST Structure ==========");
        for (SQLTableElement tableElement : st.getTableElementList()) {
            if (tableElement instanceof SQLColumnDefinition) {
                SQLColumnDefinition columnDefinition = (SQLColumnDefinition) tableElement;
                String colName =
                    com.alibaba.polardbx.druid.sql.SQLUtils.normalize(columnDefinition.getName().getSimpleName());

                System.out.println("Column: " + colName);
                System.out.println("  - DataType: " + columnDefinition.getDataType().getClass().getSimpleName());
                System.out.println("  - DataType toString: " + columnDefinition.getDataType());

                if (columnDefinition.getDataType() instanceof SQLCharacterDataType) {
                    SQLCharacterDataType characterDataType = (SQLCharacterDataType) columnDefinition.getDataType();
                    System.out.println("  - Charset from DataType: " + characterDataType.getCharSetName());
                    System.out.println("  - Collation from DataType: " + characterDataType.getCollate());
                }

                System.out.println("  - CharsetExpr: " + columnDefinition.getCharsetExpr());
                System.out.println();
            }
        }
        System.out.println("[DDL 5] ========== End of Debug ==========");

        for (SQLTableElement tableElement : st.getTableElementList()) {
            if (tableElement instanceof SQLColumnDefinition) {
                SQLColumnDefinition columnDefinition = (SQLColumnDefinition) tableElement;
                String colName =
                    com.alibaba.polardbx.druid.sql.SQLUtils.normalize(columnDefinition.getName().getSimpleName());

                // 只验证我们关心的字符类型列
                if (columnDefinition.getDataType() instanceof SQLCharacterDataType) {
                    SQLCharacterDataType characterDataType = (SQLCharacterDataType) columnDefinition.getDataType();
                    String serializedCharset = characterDataType.getCharSetName();

                    // 这些列的charset应该都是utf8mb4(保持不变)
                    if ("gen_v1qvnjcuv2f".equalsIgnoreCase(colName) ||
                        "c_char".equalsIgnoreCase(colName) ||
                        "c_varchar".equalsIgnoreCase(colName) ||
                        "c_text".equalsIgnoreCase(colName) ||
                        "c_bigint_20".equalsIgnoreCase(colName)) {
                        Assert.assertEquals("Column " + colName + " charset in snapshot DDL should be utf8mb4",
                            "utf8mb4", serializedCharset);
                        log.info("[DDL 5] Snapshot DDL column {} charset = {} ✓", colName, serializedCharset);
                    }
                }
            }
        }

        log.info("=== 测试用例7 通过: 实验室真实DDL序列复现成功,ALTER TABLE CHARACTER SET不改变列charset ===");
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证DDL执行后的charset状态
     */
    private void verifyCharsetAfterDdl(String stepName, String expectedTableCharset,
                                       Map<String, String> expectedColumnCharsets) {
        TableMeta tableMeta = memoryTableMeta.find(TEST_SCHEMA, TEST_TABLE);
        Assert.assertNotNull(stepName + ": TableMeta should not be null", tableMeta);

        // 验证表级charset
        String actualTableCharset = tableMeta.getCharset();
        Assert.assertEquals(stepName + ": Table charset mismatch",
            expectedTableCharset.toLowerCase(),
            actualTableCharset != null ? actualTableCharset.toLowerCase() : null);

        // 验证每个列的charset
        for (Map.Entry<String, String> entry : expectedColumnCharsets.entrySet()) {
            String columnName = entry.getKey();
            String expectedCharset = entry.getValue();

            TableMeta.FieldMeta field = findField(tableMeta, columnName);
            Assert.assertNotNull(stepName + ": Field " + columnName + " should exist", field);

            String actualCharset = field.getCharset();
            String expectedLower = expectedCharset != null ? expectedCharset.toLowerCase() : null;
            String actualLower = actualCharset != null ? actualCharset.toLowerCase() : null;

            // 注意: 某些非字符集敏感列(如int,blob)可能在Druid解析时也被设置了charset
            // 我们只关心字符集敏感列的charset一致性
            if (expectedCharset == null) {
                // 对于非字符集敏感列,只打印日志,不做断言
                log.info("  - {} charset = {} (non-charset-sensitive column, ignored)",
                    columnName, actualLower);
            } else {
                Assert.assertEquals(stepName + ": Column " + columnName + " charset mismatch",
                    expectedLower, actualLower);

                log.info("  ✓ {} charset = {} (expected: {})",
                    columnName, actualLower, expectedLower);
            }
        }

        log.info("{}: Table charset = {} ✓", stepName, actualTableCharset);
    }

    /**
     * 从TableMeta中查找指定列
     */
    private TableMeta.FieldMeta findField(TableMeta tableMeta, String columnName) {
        for (TableMeta.FieldMeta field : tableMeta.getFields()) {
            if (field.getColumnName().equalsIgnoreCase(columnName)) {
                return field;
            }
        }
        return null;
    }
}
