/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author yudong
 * @since 2024/8/6 15:32
 **/
@RunWith(MockitoJUnitRunner.class)
public class TableCompatibilityProcessorTest {

    @Mock
    private BinlogOssRecord record;
    @Mock
    private BinlogOssRecordMapper mapper;

    @Test
    @SneakyThrows
    public void testNeedProcessBinlogOssRecordTable() {
        try (final MockedStatic<SpringContextHolder> springContextHolder = mockStatic(SpringContextHolder.class)) {
            springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordMapper.class))
                .thenReturn(mapper);
            when(mapper.select(any(SelectDSLCompleter.class))).thenReturn(Collections.singletonList(record));
            assertTrue(TableCompatibilityProcessor.needProcessBinlogOssRecordTable());

            when(mapper.select(any(SelectDSLCompleter.class))).thenReturn(new ArrayList<>());
            assertFalse(TableCompatibilityProcessor.needProcessBinlogOssRecordTable());
        }
    }

}
