/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.BinlogDumpFilterTestBase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author zm
 * 用于测试Dumper端主动过滤功能
 */
@Slf4j
public class BinlogDumpFilterReplicaTest extends BinlogDumpFilterTestBase {

    @Test
    @SneakyThrows
    public void testSessionArchiveBinlogDumpFilter() {
        super.testSessionArchiveBinlogDumpFilter();
    }

    /**
     * 测试在开启全局的binlog_dump_archive_ignore_enabled参数下，目标表不会将TTL表的数据进行删除
     */
    @Test
    @SneakyThrows
    public void testGlobalArchiveBinlogDumpFilter() {
        super.testGlobalArchiveBinlogDumpFilter();
    }

    /**
     * 测试黑名单表过滤功能
     */
    @Test
    @SneakyThrows
    public void testTableBinlogDumpFilter() {
        super.testTableBinlogDumpFilter();
    }

    /**
     * 测试白名单表过滤功能
     */
    @Test
    @SneakyThrows
    public void testWhiteTableBinlogDumpFilter() {
        super.testWhiteTableBinlogDumpFilter();
    }

    /**
     * 测试rows query event过滤功能
     */
    @Test
    @SneakyThrows
    public void testSessionRowQueryFilter() {
        super.testSessionRowQueryFilter();
    }

    /**
     * 测试用户名参数过滤功能
     */
    @Test
    @SneakyThrows
    public void testUserParamsFilter() {
        super.testUserParamsFilter();
    }
}
