/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.SQLException;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BigTransTest extends RplBaseTestCase {

    private static final String DB_NAME = "cdc_big_trans";
    private static final String CREATE_TABLE_SQL = "CREATE TABLE `t_big_trans`\n"
        + "(`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
        + "`c_bit_1` bit(1) DEFAULT NULL,\n"
        + "`c_bit_8` bit(8) DEFAULT NULL,\n"
        + "`c_bit_16` bit(16) DEFAULT NULL,\n"
        + "`c_bit_32` bit(32) DEFAULT NULL,\n"
        + "`c_bit_64` bit(64) DEFAULT NULL,\n"
        + "`c_tinyint_1` tinyint(1) DEFAULT NULL,\n"
        + "`c_tinyint_4` tinyint(4) DEFAULT NULL,\n"
        + "`c_tinyint_8` tinyint(8) DEFAULT NULL,\n"
        + "`c_tinyint_8_un` tinyint(8) unsigned DEFAULT NULL,\n"
        + "`c_smallint_1` smallint(1) DEFAULT NULL,\n"
        + "`c_smallint_16` smallint(16) DEFAULT NULL,\n"
        + "`c_smallint_16_un` smallint(16) unsigned DEFAULT NULL,\n"
        + "`c_mediumint_1` mediumint(1) DEFAULT NULL,\n"
        + "`c_mediumint_24` mediumint(24) DEFAULT NULL,\n"
        + "`c_mediumint_24_un` mediumint(24) unsigned DEFAULT NULL,\n"
        + "`c_int_1` int(1) DEFAULT NULL,\n"
        + "`c_int_32` int(32) DEFAULT NULL,\n"
        + "`c_int_32_un` int(32) unsigned DEFAULT NULL,\n"
        + "`c_bigint_1` bigint(1) DEFAULT NULL,\n"
        + "`c_bigint_64` bigint(64) DEFAULT NULL,\n"
        + "`c_bigint_64_un` bigint(64) unsigned DEFAULT NULL,\n"
        + "`c_decimal` decimal DEFAULT NULL,\n"
        + "`c_decimal_pr` decimal(10,3) DEFAULT NULL,\n"
        + "`c_float` float DEFAULT NULL,\n"
        + "`c_float_pr` float(10,3) DEFAULT NULL,\n"
        + "`c_float_un` float(10,3) unsigned DEFAULT NULL,\n"
        + "`c_double` double DEFAULT NULL,\n"
        + "`c_double_pr` double(10,3) DEFAULT NULL,\n"
        + "`c_double_un` double(10,3) unsigned DEFAULT NULL,\n"
        + "`c_date` date DEFAULT NULL COMMENT 'date',\n"
        + "`c_datetime` datetime DEFAULT NULL,\n"
        + "`c_timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,\n"
        + "`c_time` time DEFAULT NULL,\n"
        + "`c_year` year DEFAULT NULL,\n"
        + "`c_year_4` year(4) DEFAULT NULL,\n"
        + "`c_char` char(50) DEFAULT NULL,\n"
        + "`c_varchar` varchar(50) DEFAULT NULL,\n"
        + "`c_binary` binary(200) DEFAULT NULL,\n"
        + "`c_varbinary` varbinary(200) DEFAULT NULL,\n"
        + "`c_blob_tiny` tinyblob DEFAULT NULL,\n"
        + "`c_blob` blob DEFAULT NULL,\n"
        + "`c_blob_medium` mediumblob DEFAULT NULL,\n"
        + "`c_blob_long` longblob DEFAULT NULL,\n"
        + "`c_text_tiny` tinytext DEFAULT NULL,\n"
        + "`c_text` text DEFAULT NULL,\n"
        + "`c_text_medium` mediumtext DEFAULT NULL,\n"
        + "`c_text_long` longtext DEFAULT NULL,\n"
        + "`c_enum` enum('a','b','c') DEFAULT NULL,\n"
        + "`c_set` set('a','b','c') DEFAULT NULL,\n"
        + "`c_testcase` varchar(10) DEFAULT NULL,\n"
        + "PRIMARY KEY (`id`) ) \n"
        + "ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='10000000' dbpartition by hash(`id`) tbpartition by hash(`id`) tbpartitions 8";
    private static final String INSERT_SINGLE_SQL =
        "INSERT t_big_trans (c_bit_1 , c_bit_8 , c_bit_16 , c_bit_32 , c_bit_64 , c_tinyint_1 , c_tinyint_4 , c_tinyint_8 , c_tinyint_8_un , c_smallint_1 , c_smallint_16 , c_smallint_16_un , c_mediumint_1 , c_mediumint_24 , c_mediumint_24_un , c_int_1 , c_int_32 , c_int_32_un , c_bigint_1 , c_bigint_64 , c_bigint_64_un , c_decimal , c_decimal_pr , c_float , c_float_pr , c_float_un , c_double , c_double_pr , c_double_un , c_date , c_datetime , c_timestamp , c_time , c_year , c_year_4 , c_char , c_varchar , c_binary , c_varbinary , c_blob_tiny , c_blob , c_blob_medium , c_blob_long , c_text_tiny , c_text , c_text_medium , c_text_long , c_enum , c_set , c_testcase) \n"
            + "VALUES( \n"
            + "b'1' , b'11111111' , b'1111111111111111' , b'11111111111111111111111111111111' , b'1111111111111111111111111111111111111111111111111111111111111111' , '82' , '-101' , '-75' ,'253' , '14497' , '5070' , '9427' , '-5888259' , '-1845105' , '2350597' , '-2147483648' , '-2147483648' , '3653062473' , '-816854218224922624' , '7292572323853307904' , '2902765992697493504' , '-1613793319' , '1223077.292' , '9.1096275E8' , '-5839673.5' , '2648.644' , '4.334081673614155E9' , '6973286.176' , '7630560.182' , '2019-02-15' , '2019-02-15 14:54:41' , '2019-02-15 14:54:41' , '20:12:46' , '2019' , '2019', 'xxx','int a = max(4,5)','xxxxxxx','varchar a = ','xxkdkwjsjdfdsk','varchar a = ',';sdjfaljdljfsljldjlsjdljflsjdlfjsaljdlfhahdljflajdlfasdf','int a = max(4,5)','xxx','sdjflshdflsjlfa;;dfjadjfahdfhklsajdfklsafasjlfjls',repeat('a',1024),repeat('a',1024), 'a', 'a', 'caseNo1')";
    private static final String INSERT_SELECT_SQL =
        "INSERT t_big_trans ( c_bit_1 ,  c_bit_8 ,  c_bit_16 ,  c_bit_32 ,  c_bit_64 ,  c_tinyint_1 ,  c_tinyint_4 ,  c_tinyint_8 ,  c_tinyint_8_un ,  c_smallint_1 ,\n"
            + "c_smallint_16 ,  c_smallint_16_un ,  c_mediumint_1 ,  c_mediumint_24 ,  c_mediumint_24_un ,  c_int_1 ,  c_int_32 ,  c_int_32_un ,  c_bigint_1 ,  c_bigint_64\n"
            + ",  c_bigint_64_un ,  c_decimal ,  c_decimal_pr ,  c_float ,  c_float_pr ,  c_float_un ,  c_double ,  c_double_pr ,  c_double_un ,  c_date ,  c_datetime ,  c_timestamp ,  c_time ,  c_year ,  c_year_4 ,  c_char ,  c_varchar ,  c_binary ,  c_varbinary ,  c_blob_tiny ,  c_blob ,  c_blob_medium ,  c_blob_long ,  c_text_tiny ,  c_text ,  c_text_medium ,  c_text_long ,  c_enum ,  c_set ,  c_testcase) select c_bit_1 ,  c_bit_8 ,  c_bit_16 ,  c_bit_32 ,  c_bit_64 ,  c_tinyint_1 ,  c_tinyint_4 ,  c_tinyint_8 ,  c_tinyint_8_un ,  c_smallint_1 ,  c_smallint_16 ,  c_smallint_16_un ,  c_mediumint_1 ,  c_mediumint_24 ,  c_mediumint_24_un ,  c_int_1 ,  c_int_32 ,  c_int_32_un ,  c_bigint_1 ,  c_bigint_64 ,  c_bigint_64_un ,  c_decimal ,  c_decimal_pr ,  c_float ,  c_float_pr ,  c_float_un ,  c_double ,  c_double_pr ,  c_double_un ,  c_date ,  c_datetime ,  c_timestamp ,  c_time ,  c_year ,  c_year_4 ,  c_char ,  c_varchar ,  c_binary ,  c_varbinary ,  c_blob_tiny ,  c_blob ,  c_blob_medium ,  c_blob_long ,  c_text_tiny ,  c_text ,  c_text_medium ,  c_text_long ,  c_enum ,  c_set ,  c_testcase from t_big_trans";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @Test
    @Ignore
    public void testBigTrans() throws SQLException, InterruptedException {
        JdbcUtil.executeUpdate(polardbxConnection, CREATE_TABLE_SQL);
        JdbcUtil.executeSuccess(polardbxConnection, INSERT_SINGLE_SQL);
        polardbxConnection.setAutoCommit(false);
        for (int i = 0; i < 21; i++) {
            JdbcUtil.executeSuccess(polardbxConnection, INSERT_SELECT_SQL);
        }
        polardbxConnection.commit();
        Thread.sleep(360 * 1000);//避免其它用例wait token超时，加一个sleep
    }
}
