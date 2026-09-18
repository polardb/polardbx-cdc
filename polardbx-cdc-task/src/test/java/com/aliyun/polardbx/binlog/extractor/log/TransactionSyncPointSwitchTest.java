/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.domain.po.CdcSyncPointMeta;
import com.aliyun.polardbx.binlog.service.CdcSyncPointMetaService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 sync point 开关功能：
 * - 开关关闭时（默认），sync point 表事件被识别但不处理，事务不标记为 syncPoint
 * - 开关开启时，sync point 表事件正常处理，事务被标记为 syncPoint
 */
public class TransactionSyncPointSwitchTest extends BaseTest {

    /**
     * 测试 sync point 开关关闭时，事务不会被标记为 syncPoint
     */
    @Test
    @SneakyThrows
    public void testSyncPointDisabled() {
        // 默认配置：task_sync_point_enabled=false
        mockConfig(ConfigKeys.TASK_SYNC_POINT_ENABLED, "false");
        mockConfig(ConfigKeys.TASK_PROCESS_SYNC_POINT_WAIT_TIMEOUT_MILLISECOND, "5000");

        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(
            TransactionTest.class.getClassLoader().getResource("binlog/mysql_bin.2").toURI()), 985);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("mysql_bin.2", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        // FORMAT_DESCRIPTION_EVENT
        fetcher.fetch();
        logDecoder.decode(fetcher.buffer(), logContext);
        // xa start
        fetcher.fetch();
        LogEvent xaStartEvent = logDecoder.decode(fetcher.buffer(), logContext);
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder("pxc-xxx"));
        Transaction transaction = new Transaction(null, xaStartEvent, runtimeContext) {
            @Override
            void buildBuffer() {
            }
        };
        // table mapping
        LogEvent tableMappingEvent = logDecoder.decode(fetcher.buffer(), logContext);
        transaction.processEvent(tableMappingEvent, runtimeContext);
        // write rows - sync point table event
        LogEvent writeRowsEvent = logDecoder.decode(fetcher.buffer(), logContext);

        // 即使 CdcSyncPointMetaService 可用，开关关闭时也不会调用它
        try (MockedStatic<SpringContextHolder> springContextHolderMockedStatic =
            Mockito.mockStatic(SpringContextHolder.class, CALLS_REAL_METHODS)) {
            CdcSyncPointMetaService service = mock(CdcSyncPointMetaService.class);
            when(getObject(CdcSyncPointMetaService.class)).thenReturn(service);
            CdcSyncPointMeta meta = new CdcSyncPointMeta();
            meta.setValid(1);
            meta.setId("test-id");
            when(service.selectById(Mockito.anyString())).thenReturn(Optional.of(meta));

            transaction.processEvent(writeRowsEvent, runtimeContext);
        }

        // 开关关闭时，事务不应被标记为 syncPoint
        Assert.assertFalse(transaction.isSyncPoint());
        // holdingTso 不应被设置
        Assert.assertFalse(runtimeContext.inSyncPointTxn());
    }
}
