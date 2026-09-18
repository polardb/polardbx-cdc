/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.AddressUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.FilterType;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;

import static com.aliyun.polardbx.binlog.ConfigKeys.STREAM_NAME;
import static org.mockito.Mockito.*;

/**
 * RplTaskRunner init() 方法中 streamName 逻辑的单元测试
 */
public class RplTaskRunnerTest extends BaseTest {

    private JdbcTemplate polarxJdbcTemplate = mock(JdbcTemplate.class);

    @Before
    public void setUp() {
        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        // 清理 STREAM_NAME 系统属性
        System.clearProperty(STREAM_NAME);
        // 重置 TaskContext 单例（避免跨测试污染）
        resetTaskContextInstance();
    }

    @After
    public void tearDown() {
        System.clearProperty(STREAM_NAME);
        unregisterSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
    }

    /**
     * 通过反射重置 TaskContext 单例 instance 为 null
     */
    private void resetTaskContextInstance() {
        try {
            Field instanceField = TaskContext.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 通过反射调用 RplTaskRunner.init()
     */
    private void invokeInit(RplTaskRunner runner) throws Exception {
        Method initMethod = RplTaskRunner.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(runner);
    }

    // ============= RPL_FILTER 分支测试 =============

    /**
     * 测试 init() - RPL_FILTER 且 streamName 非空 → 应设置 System property
     */
    @Test
    public void testInit_RplFilter_WithStreamName() throws Exception {
        long taskId = 1L;

        // 准备 RplTask
        RplTask task = new RplTask();
        task.setId(taskId);
        task.setServiceId(10L);
        task.setStateMachineId(100L);

        // 准备 ExtractorConfig (RPL_FILTER + streamName)
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setStreamName("my_stream_rpl");

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setFilterType(FilterType.RPL_FILTER);
        extractorConfig.setPrivateMeta(JSON.toJSONString(replicaMeta));

        RplTaskConfig taskConfig = new RplTaskConfig();
        taskConfig.setExtractorConfig(JSON.toJSONString(extractorConfig));

        // 准备 Service / StateMachine
        RplService service = new RplService();
        service.setId(10L);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(100L);
        stateMachine.setConfig("{}");

        InetAddress mockInet = mock(InetAddress.class);
        when(mockInet.getHostAddress()).thenReturn("127.0.0.1");

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<AddressUtil> mockedAddr = mockStatic(AddressUtil.class)) {

            mockedDb.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(taskConfig);
            mockedDb.when(() -> DbTaskMetaManager.getService(10L)).thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.getStateMachine(100L)).thenReturn(stateMachine);
            mockedAddr.when(AddressUtil::getHostAddress).thenReturn(mockInet);

            RplTaskRunner runner = new RplTaskRunner(taskId);
            try {
                invokeInit(runner);
            } catch (Exception e) {
                // init() 会在 createFilter()/createExtractor() 等后续调用中失败，这是预期的
                // 我们只关心 streamName 逻辑是否正确执行
            }

            // 验证 System.setProperty 被正确调用
            Assert.assertEquals("my_stream_rpl", System.getProperty(STREAM_NAME));
        }
    }

    /**
     * 测试 init() - RPL_FILTER 且 streamName 为空白 → 不应设置 System property
     */
    @Test
    public void testInit_RplFilter_WithBlankStreamName() throws Exception {
        long taskId = 2L;

        RplTask task = new RplTask();
        task.setId(taskId);
        task.setServiceId(20L);
        task.setStateMachineId(200L);

        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setStreamName(""); // 空白

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setFilterType(FilterType.RPL_FILTER);
        extractorConfig.setPrivateMeta(JSON.toJSONString(replicaMeta));

        RplTaskConfig taskConfig = new RplTaskConfig();
        taskConfig.setExtractorConfig(JSON.toJSONString(extractorConfig));

        RplService service = new RplService();
        service.setId(20L);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(200L);
        stateMachine.setConfig("{}");

        InetAddress mockInet = mock(InetAddress.class);
        when(mockInet.getHostAddress()).thenReturn("127.0.0.1");

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<AddressUtil> mockedAddr = mockStatic(AddressUtil.class)) {

            mockedDb.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(taskConfig);
            mockedDb.when(() -> DbTaskMetaManager.getService(20L)).thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.getStateMachine(200L)).thenReturn(stateMachine);
            mockedAddr.when(AddressUtil::getHostAddress).thenReturn(mockInet);

            RplTaskRunner runner = new RplTaskRunner(taskId);
            try {
                invokeInit(runner);
            } catch (Exception e) {
                // 预期后续步骤失败
            }

            // streamName 为空白 → 不应设置 System property
            Assert.assertNull(System.getProperty(STREAM_NAME));
        }
    }

    /**
     * 测试 init() - RPL_FILTER 且 streamName 为 null → 不应设置 System property
     */
    @Test
    public void testInit_RplFilter_WithNullStreamName() throws Exception {
        long taskId = 3L;

        RplTask task = new RplTask();
        task.setId(taskId);
        task.setServiceId(30L);
        task.setStateMachineId(300L);

        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setStreamName(null); // null

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setFilterType(FilterType.RPL_FILTER);
        extractorConfig.setPrivateMeta(JSON.toJSONString(replicaMeta));

        RplTaskConfig taskConfig = new RplTaskConfig();
        taskConfig.setExtractorConfig(JSON.toJSONString(extractorConfig));

        RplService service = new RplService();
        service.setId(30L);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(300L);
        stateMachine.setConfig("{}");

        InetAddress mockInet = mock(InetAddress.class);
        when(mockInet.getHostAddress()).thenReturn("127.0.0.1");

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<AddressUtil> mockedAddr = mockStatic(AddressUtil.class)) {

            mockedDb.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(taskConfig);
            mockedDb.when(() -> DbTaskMetaManager.getService(30L)).thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.getStateMachine(300L)).thenReturn(stateMachine);
            mockedAddr.when(AddressUtil::getHostAddress).thenReturn(mockInet);

            RplTaskRunner runner = new RplTaskRunner(taskId);
            try {
                invokeInit(runner);
            } catch (Exception e) {
                // 预期后续步骤失败
            }

            Assert.assertNull(System.getProperty(STREAM_NAME));
        }
    }

    // ============= IMPORT_FILTER 分支测试 =============

    /**
     * 测试 init() - IMPORT_FILTER 且 streamName 非空 → 应设置 System property
     */
    @Test
    public void testInit_ImportFilter_WithStreamName() throws Exception {
        long taskId = 4L;

        RplTask task = new RplTask();
        task.setId(taskId);
        task.setServiceId(40L);
        task.setStateMachineId(400L);

        // 准备 PhysicalMeta with streamName
        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setStreamName("my_stream_import");

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setFilterType(FilterType.IMPORT_FILTER);
        extractorConfig.setPrivateMeta(JSON.toJSONString(physicalMeta));

        RplTaskConfig taskConfig = new RplTaskConfig();
        taskConfig.setExtractorConfig(JSON.toJSONString(extractorConfig));

        // DataImportMeta 需要有 metaList（用于 meta.getMetaList().size()）
        DataImportMeta dataImportMeta = new DataImportMeta();
        dataImportMeta.setMetaList(new ArrayList<>(Collections.singletonList(physicalMeta)));

        RplService service = new RplService();
        service.setId(40L);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(400L);
        stateMachine.setConfig(JSON.toJSONString(dataImportMeta));

        InetAddress mockInet = mock(InetAddress.class);
        when(mockInet.getHostAddress()).thenReturn("127.0.0.1");

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<AddressUtil> mockedAddr = mockStatic(AddressUtil.class)) {

            mockedDb.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(taskConfig);
            mockedDb.when(() -> DbTaskMetaManager.getService(40L)).thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.getStateMachine(400L)).thenReturn(stateMachine);
            mockedAddr.when(AddressUtil::getHostAddress).thenReturn(mockInet);

            RplTaskRunner runner = new RplTaskRunner(taskId);
            try {
                invokeInit(runner);
            } catch (Exception e) {
                // 预期后续步骤失败
            }

            Assert.assertEquals("my_stream_import", System.getProperty(STREAM_NAME));
        }
    }

    /**
     * 测试 init() - IMPORT_FILTER 且 streamName 为空白 → 不应设置 System property
     */
    @Test
    public void testInit_ImportFilter_WithBlankStreamName() throws Exception {
        long taskId = 5L;

        RplTask task = new RplTask();
        task.setId(taskId);
        task.setServiceId(50L);
        task.setStateMachineId(500L);

        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setStreamName("  "); // 空白

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setFilterType(FilterType.IMPORT_FILTER);
        extractorConfig.setPrivateMeta(JSON.toJSONString(physicalMeta));

        RplTaskConfig taskConfig = new RplTaskConfig();
        taskConfig.setExtractorConfig(JSON.toJSONString(extractorConfig));

        DataImportMeta dataImportMeta = new DataImportMeta();
        dataImportMeta.setMetaList(new ArrayList<>(Collections.singletonList(physicalMeta)));

        RplService service = new RplService();
        service.setId(50L);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(500L);
        stateMachine.setConfig(JSON.toJSONString(dataImportMeta));

        InetAddress mockInet = mock(InetAddress.class);
        when(mockInet.getHostAddress()).thenReturn("127.0.0.1");

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<AddressUtil> mockedAddr = mockStatic(AddressUtil.class)) {

            mockedDb.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(taskConfig);
            mockedDb.when(() -> DbTaskMetaManager.getService(50L)).thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.getStateMachine(500L)).thenReturn(stateMachine);
            mockedAddr.when(AddressUtil::getHostAddress).thenReturn(mockInet);

            RplTaskRunner runner = new RplTaskRunner(taskId);
            try {
                invokeInit(runner);
            } catch (Exception e) {
                // 预期后续步骤失败
            }

            // streamName 为空白 → 不应设置
            Assert.assertNull(System.getProperty(STREAM_NAME));
        }
    }
}
