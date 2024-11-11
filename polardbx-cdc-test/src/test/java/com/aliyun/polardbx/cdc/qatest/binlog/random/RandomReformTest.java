/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.BigDecimalGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.BlobGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.BooleanGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.ByteGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.BytesGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.DateGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.DoubleGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.EnumGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.FloatGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.GeoGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.IntegerGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.JsonGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.LongGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.SetGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.ShortGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.StringGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.TimestampGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.YearGenerator;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BIGINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BINARY;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BIT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BLOB;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BOOLEAN;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_CHAR;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DATE;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DATETIME;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DEC;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DECIMAL;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DOUBLE;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_ENUM;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_FLOAT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_GEO;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_INT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_JSON;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_LONGBLOB;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_LONGTEXT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_MEDIUMBLOB;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_MEDIUMINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_MEDIUMTEXT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_SET;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_SMALLINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TEXT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TIME;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TIMESTAMP;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TINYBLOB;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TINYINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TINYTEXT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_VARBINARY;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_VARCAHR;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_YEAR;

@Ignore
public class RandomReformTest extends BaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(RandomReformTest.class);
    private LogicTableMeta logicTableMeta;
    private String dbName = "random_dml_test";
    private String DROP_DB_DDL = "drop database  if exists random_dml_test";
    private String INIT_DB_DDL = "create database random_dml_test;";
    private String USE_DB = "use random_dml_test;";
    private AtomicBoolean runnable = new AtomicBoolean(true);
    private CountDownLatch countDownLatch;
    private ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "random_test_thread");
        return t;
    });

    private int randomDDLRate = 50;

    @Before
    public void before() {
        logger.info("register data value generator");
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_VARCAHR));
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_CHAR));
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_TINYTEXT));
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_TEXT));
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_MEDIUMTEXT));
        ColumnTypeEnum.registerGenerator(new StringGenerator(TYPE_LONGTEXT));
        ColumnTypeEnum.registerGenerator(new ShortGenerator(TYPE_SMALLINT));
        ColumnTypeEnum.registerGenerator(new IntegerGenerator(TYPE_INT));
        ColumnTypeEnum.registerGenerator(new ByteGenerator(TYPE_TINYINT));
        ColumnTypeEnum.registerGenerator(new ShortGenerator(TYPE_MEDIUMINT));
        ColumnTypeEnum.registerGenerator(new LongGenerator(TYPE_BIGINT));
        ColumnTypeEnum.registerGenerator(new BooleanGenerator(TYPE_BOOLEAN));
        ColumnTypeEnum.registerGenerator(new BigDecimalGenerator(TYPE_DECIMAL));
        ColumnTypeEnum.registerGenerator(new BigDecimalGenerator(TYPE_DEC));
        ColumnTypeEnum.registerGenerator(new FloatGenerator(TYPE_FLOAT));
        ColumnTypeEnum.registerGenerator(new DoubleGenerator(TYPE_DOUBLE));
        ColumnTypeEnum.registerGenerator(new SetGenerator(TYPE_SET));
        ColumnTypeEnum.registerGenerator(new EnumGenerator(TYPE_ENUM));
        ColumnTypeEnum.registerGenerator(new JsonGenerator(TYPE_JSON));
        ColumnTypeEnum.registerGenerator(new GeoGenerator(TYPE_GEO));
        ColumnTypeEnum.registerGenerator(new ByteGenerator(TYPE_BIT));
        ColumnTypeEnum.registerGenerator(new BlobGenerator(TYPE_TINYBLOB));
        ColumnTypeEnum.registerGenerator(new BlobGenerator(TYPE_BLOB));
        ColumnTypeEnum.registerGenerator(new BlobGenerator(TYPE_MEDIUMBLOB));
        ColumnTypeEnum.registerGenerator(new BlobGenerator(TYPE_LONGBLOB));
        ColumnTypeEnum.registerGenerator(new BytesGenerator(TYPE_BINARY));
        ColumnTypeEnum.registerGenerator(new BytesGenerator(TYPE_VARBINARY));

        ColumnTypeEnum.registerGenerator(new TimestampGenerator(TYPE_TIME));
        ColumnTypeEnum.registerGenerator(new TimestampGenerator(TYPE_TIMESTAMP));
        ColumnTypeEnum.registerGenerator(new DateGenerator(TYPE_DATETIME));
        ColumnTypeEnum.registerGenerator(new DateGenerator(TYPE_DATE));
        ColumnTypeEnum.registerGenerator(new YearGenerator(TYPE_YEAR));

        logicTableMeta = new LogicTableMeta(ColumnTypeEnum.allType());
    }

    private void prepare() throws SQLException {
        runnable.set(true);
        Connection conn = getPolardbxConnection();
        try {
            Statement st = conn.createStatement();
            st.executeUpdate(DROP_DB_DDL);
            st.executeUpdate(INIT_DB_DDL);
            st.executeQuery(USE_DB);
            logger.info("prepare database random_dml_test  successed !");
            logicTableMeta.initTable(conn);
            logger.info("prepare table " + logicTableMeta.getTableName() + " successed !");
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    private void randomDML() {
        logger.info("do random dml");
        // 进来复用一个，避免OOM
        Connection conn = getPolardbxConnection();
        while (runnable.get()) {
            try {
                logicTableMeta.insert(conn);
            } catch (Throwable t) {
                if (t.getMessage().contains("Unknown target column")) {
                    continue;
                }
                logger.error("do dml failed!", t);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException throwables) {

            }
        }
    }

    private void randomDDL() {
        logger.info("do random ddl");
        Connection conn = getPolardbxConnection();
        while (runnable.get()) {
            try {
                int randomValue = RandomUtils.nextInt(0, 100);
                //调整概率
                //50% changetype
                //25% dropColumn
                //25% addColumn
                if (randomValue < 50) {
                    logicTableMeta.randomModifyColumnType(conn);
                } else if (randomValue < 75) {
                    logicTableMeta.randomDropColumn(conn);
                } else {
                    logicTableMeta.randomAddColumn(conn);
                }
                Thread.sleep(1000L);
            } catch (Throwable t) {
                logger.error("do dml failed!", t);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException throwables) {
            }
        }

    }

    private void checkData() throws Exception {
        Pair<String, String> tablePair =
            new ImmutablePair<>(dbName, logicTableMeta.getTableName());
        checkOneTable(tablePair);
    }

    /**
     * 创建分表
     * 不停的 insert/update
     * 随机执行 ddl
     */
    @Test
    public void doRandomTest() throws Exception {
        prepare();
        countDownLatch = new CountDownLatch(3);
        threadPoolExecutor.execute(() -> {
            try {
                randomDML();
            } finally {
                countDownLatch.countDown();
            }
        });
        threadPoolExecutor.execute(() -> {
            try {
                randomDML();
            } finally {
                countDownLatch.countDown();
            }
        });
        threadPoolExecutor.execute(() -> {
            try {
                randomDDL();
            } finally {
                countDownLatch.countDown();
            }
        });
        // 跑10分钟
        Thread.sleep(TimeUnit.MINUTES.toMillis(10));
        runnable.set(false);
        threadPoolExecutor.shutdown();
        // 等待30s
        threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS);
        countDownLatch.await();

        // 等待10min，数据应该都同步完成
        waitForSync(30, TimeUnit.MINUTES);
        checkData();
    }

}
