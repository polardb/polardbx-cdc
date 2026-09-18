/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import org.junit.*;
import org.mockito.*;

import javax.sql.DataSource;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PERSIST_ENABLED;
import static org.mockito.Mockito.*;

public class MysqlBinlogExtractorTest extends BaseTest {
    private MysqlBinlogExtractor mysqlBinlogExtractor;

    @Mock
    private HostInfo metaHostInfo = new HostInfo("127.0.0.1", 3306, "user", "pass", "schema", HostType.MYSQL, 1);

    @Mock
    private BinlogPosition position = mock(BinlogPosition.class);

    @Mock
    private BaseFilter filter = mock(BaseFilter.class);

    @Mock
    private AbstractEventParser parser = mock(AbstractEventParser.class);

    @Mock
    private ReplicaMeta replicaMeta = new ReplicaMeta();

    @Mock
    private final HostInfo srcHostInfo = new HostInfo("127.0.0.1", 3306, "user", "pass", "schema", HostType.MYSQL, 1);
    private final ExtractorConfig extractorConfig = mock(ExtractorConfig.class);
    private final BasePipeline pipeline = mock(BasePipeline.class);
    private final PipelineConfig pipelineConfig = mock(PipelineConfig.class);

    @Mock
    private RplEventRepository rplEventRepository = mock(RplEventRepository.class);

    @Before
    public void setUp() throws Exception {
        mockConfig(RPL_PERSIST_ENABLED, "true");
        replicaMeta.setIgnoreServerIds("123");
        replicaMeta.setServerId("456");
        when(pipeline.getPipeLineConfig()).thenReturn(pipelineConfig);
        PersistConfig config = new PersistConfig();
        when(pipelineConfig.getPersistConfig()).thenReturn(config);
        when(extractorConfig.getPrivateMeta()).thenReturn(JSON.toJSONString(replicaMeta));
        when(extractorConfig.getEventBufferSize()).thenReturn(2048);
        when(extractorConfig.isEnableSrcLogicalMetaSnapshot()).thenReturn(true);

        // 初始化注入字段
        mysqlBinlogExtractor = new MysqlBinlogExtractor(extractorConfig, srcHostInfo, metaHostInfo, position, filter);
        mysqlBinlogExtractor.setPipeline(pipeline);
    }

    @Test
    public void testSetIgnoreIds() throws Exception {
        // 执行测试
        try (MockedStatic<DataSourceUtil> dataSourceUtilMockedStatic = mockStatic(DataSourceUtil.class)) {
            DataSource dataSource = mock(DruidDataSource.class);
            dataSourceUtilMockedStatic.when(() -> DataSourceUtil.createDruidMySqlDataSource(
                anyBoolean(), // boolean usePolarxPoolCN
                anyString(),  // String ip
                anyInt(),     // int port
                anyString(),  // String dbName
                anyString(),  // String user
                anyString(),  // String passwd
                anyString(),  // String encoding
                anyInt(),     // int minPoolSize
                anyInt(),     // int maxPoolSize
                anyBoolean(), // boolean longSql
                anyMap(),     // Map<String, String> params
                anyList()     // List<String> newConnectionSQLs
            )).thenReturn(dataSource);
            mysqlBinlogExtractor.start();
            Assert.assertEquals("123", mysqlBinlogExtractor.getParser().getIgnoreServerIds());
            Assert.assertEquals("456", mysqlBinlogExtractor.getParser().getWriteServerId());
        }

    }

}
