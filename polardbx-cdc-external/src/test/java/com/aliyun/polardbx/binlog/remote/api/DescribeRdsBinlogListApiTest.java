/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api;

import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.DescribeRdsBinlogListApi;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.dbs.ArchiveLogPages;
import com.aliyun.polardbx.binlog.api.dbs.DbsBinlogFile;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DescribeRdsBinlogListApiTest extends BaseTest {


    @BeforeClass
    public static void setUp(){
        System.setProperty("dbs_api_url", "test");
        System.setProperty("dbs_api_access_id", "test");
        System.setProperty("dbs_api_access_key", "test");
        System.setProperty("dbs_region_code", "cn-hangzhou");
    }

    @Test
    public void testRdsApi() throws Exception {
        try(MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)){
            DescribeBinlogFilesResult result = new DescribeBinlogFilesResult();
            ArchiveLogPages pages = new ArchiveLogPages();
            pages.setPageNumber(1);
            pages.setTotalElements(1);
            pages.setPageSize(1);
            BinlogFile dbsBinlogFile = new BinlogFile();
            List<BinlogFile> rdsBinlogFiles = new ArrayList<>();
            rdsBinlogFiles.add(dbsBinlogFile);
            result.setItems(rdsBinlogFiles);
            result.setTotalRecords(1);
            result.setItemsNumbers(1);
            result.setPageNumbers(1);
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));
            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles("test-instance", "111", "222", startTime, endTime, 200, 1)).thenReturn(result);
            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles("test-instance", "111", "222", now - 1000, now, 200, false);
            Assert.assertEquals(rdsBinlogFiles.size(), binlogFiles.size());
        }

    }

    @Test
    public void testDbsApi() throws Exception {
        try(MockedStatic<DbsApi> dbsApi = Mockito.mockStatic(DbsApi.class)){
            DescribeUnifyArchiveLogFilesResult result = new DescribeUnifyArchiveLogFilesResult();
            ArchiveLogPages pages = new ArchiveLogPages();
            pages.setPageNumber(1);
            pages.setTotalElements(1);
            pages.setPageSize(1);
            DbsBinlogFile dbsBinlogFile = new DbsBinlogFile();
            List<DbsBinlogFile> dbsBinlogFiles = new ArrayList<>();
            dbsBinlogFiles.add(dbsBinlogFile);
            pages.setContent(dbsBinlogFiles);
            result.setData(pages);
            long now = System.currentTimeMillis();
            dbsApi.when(() -> DbsApi.describeUnifyArchiveLogFiles("test-instance", "111", "222", now - 1000, now, 200, 1)).thenReturn(result);
            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles("test-instance", "111", "222", now - 1000, now, 200, true);
            Assert.assertEquals(dbsBinlogFiles.size(), binlogFiles.size());
        }
    }
}
