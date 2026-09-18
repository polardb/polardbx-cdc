/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.binlog;

import com.aliyun.polardbx.cdc.qatest.base.BinlogDumpFilterTestBase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * 相比于replica中的测试，去除了ignore_table的相关用例，对该表进行操作会导致mysql链路断开（全局过滤导致位点不一致）
 *
 * @author zm
 */
@Slf4j
public class BinlogDumpFilterMysqlTest extends BinlogDumpFilterTestBase {

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
