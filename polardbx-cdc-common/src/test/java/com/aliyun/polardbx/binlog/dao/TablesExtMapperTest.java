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

package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.TablesExt;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.mybatis.dynamic.sql.where.condition.IsNull;
import org.springframework.context.ApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 *
 **/
public class TablesExtMapperTest {

    private ApplicationContext context;

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void test() {
        TablesExtMapper mapper = SpringContextHolder.getObject(TablesExtMapper.class);
        TablesExt t = mapper.selectByPrimaryKey(1133L).get();
        System.out.println(t);
    }

    @Test
    public void testBinlog() {
        BinlogOssRecordMapper ossRecordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> notUploadBinlogList =
            ossRecordMapper.select(s -> s.where(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                IsEqualTo.of(() -> 0))
                .and(BinlogOssRecordDynamicSqlSupport.uploadHost, new IsNull())
                .orderBy(BinlogOssRecordDynamicSqlSupport.id.descending()));

        for (BinlogOssRecord rec : notUploadBinlogList) {
            System.out.println(new Gson().toJson(rec));
        }
    }

    @Test
    public void testBinlogInsert() {
        BinlogOssRecordMapper ossRecordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog-0004");
        record.setUploadHost(CommonUtils.getHostIp());
        int ret = ossRecordMapper.insert(record);
        BinlogOssRecord oldRecord = ossRecordMapper.selectOne(s -> s.where(BinlogOssRecordDynamicSqlSupport.binlogFile,
            SqlBuilder.isEqualTo(record.getBinlogFile()))).get();
        System.out.println(ret + " , " + oldRecord.getId());
    }

    @Test
    public void testResolver() throws SQLException, InterruptedException {
        System.out.println(SpringContextHolder.getPropertiesValue("meta.datasource.url"));
    }

    @Test
    public void testCon() throws SQLException, InterruptedException {

        Connection connection =
            DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/polardbx_meta_db_polardbx",
                "diamond",
                "diamond1qaz@2wsx");

        final Statement statement = connection.createStatement();

        final ResultSet resultSet = statement.executeQuery("SELECT GET_LOCK('lock2',2)");
        while (resultSet.next()) {
            System.out.println(resultSet.getInt(1));
        }

    }
}
