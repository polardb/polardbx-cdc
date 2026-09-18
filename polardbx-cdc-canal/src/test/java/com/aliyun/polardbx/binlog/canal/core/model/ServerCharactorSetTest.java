/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ServerCharactorSetTest extends BaseTest {

    /**
     * 测试正常场景：所有字符集都设置成功
     */
    @Test
    public void testLoadCharactorSetFromCN_AllCharsetSet() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("character_set_client", "utf8mb4"),
                Pair.of("character_set_connection", "utf8mb4"),
                Pair.of("character_set_database", "latin1"),
                Pair.of("character_set_server", "utf8")
            );

            // Mock jdbcTemplate.query 方法
            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8mb4", result.getCharacterSetClient());
            Assert.assertEquals("utf8mb4", result.getCharacterSetConnection());
            Assert.assertEquals("latin1", result.getCharacterSetDatabase());
            Assert.assertEquals("utf8", result.getCharacterSetServer());
        }
    }

    /**
     * 测试utf8mb3转换为utf8的场景
     */
    @Test
    public void testLoadCharactorSetFromCN_Utf8mb3Conversion() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据 - 使用utf8mb3
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("character_set_client", "utf8mb3"),
                Pair.of("character_set_connection", "utf8mb3"),
                Pair.of("character_set_database", "utf8mb3"),
                Pair.of("character_set_server", "utf8mb3")
            );

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果 - utf8mb3应该被转换为utf8
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8", result.getCharacterSetClient());
            Assert.assertEquals("utf8", result.getCharacterSetConnection());
            Assert.assertEquals("utf8", result.getCharacterSetDatabase());
            Assert.assertEquals("utf8", result.getCharacterSetServer());
        }
    }

    /**
     * 测试部分字符集设置的场景
     */
    @Test
    public void testLoadCharactorSetFromCN_PartialCharsetSet() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据 - 只设置部分字符集
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("character_set_client", "gbk"),
                Pair.of("character_set_connection", "gb2312")
                // 缺少 character_set_database 和 character_set_server
            );

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果 - 未设置的字段应该保持默认值"utf8"
            Assert.assertNotNull(result);
            Assert.assertEquals("gbk", result.getCharacterSetClient());
            Assert.assertEquals("gb2312", result.getCharacterSetConnection());
            Assert.assertEquals("utf8", result.getCharacterSetDatabase()); // 默认值
            Assert.assertEquals("utf8", result.getCharacterSetServer()); // 默认值
        }
    }

    /**
     * 测试空结果的场景
     */
    @Test
    public void testLoadCharactorSetFromCN_EmptyResult() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 返回空列表
            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果 - 所有字段应该保持默认值"utf8"
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8", result.getCharacterSetClient());
            Assert.assertEquals("utf8", result.getCharacterSetConnection());
            Assert.assertEquals("utf8", result.getCharacterSetDatabase());
            Assert.assertEquals("utf8", result.getCharacterSetServer());
        }
    }

    /**
     * 测试包含无关变量的场景
     */
    @Test
    public void testLoadCharactorSetFromCN_WithIrrelevantVariables() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据 - 包含相关和无关的变量
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("character_set_client", "utf8mb4"),
                Pair.of("character_set_filesystem", "binary"), // 无关变量
                Pair.of("character_set_connection", "utf8"),
                Pair.of("character_set_results", "utf8mb4"), // 无关变量
                Pair.of("character_set_database", "latin1"),
                Pair.of("character_set_system", "utf8") // 无关变量
            );

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果 - 只有相关的字段被设置，无关变量被忽略
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8mb4", result.getCharacterSetClient());
            Assert.assertEquals("utf8", result.getCharacterSetConnection());
            Assert.assertEquals("latin1", result.getCharacterSetDatabase());
            Assert.assertEquals("utf8", result.getCharacterSetServer()); // 保持默认值
        }
    }

    /**
     * 测试变量名大小写不敏感
     */
    @Test
    public void testLoadCharactorSetFromCN_CaseInsensitive() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据 - 使用不同大小写
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("CHARACTER_SET_CLIENT", "utf8mb4"),
                Pair.of("Character_Set_Connection", "utf8"),
                Pair.of("character_set_database", "latin1"),
                Pair.of("CHARACTER_SET_SERVER", "gbk")
            );

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果 - 大小写不应该影响结果
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8mb4", result.getCharacterSetClient());
            Assert.assertEquals("utf8", result.getCharacterSetConnection());
            Assert.assertEquals("latin1", result.getCharacterSetDatabase());
            Assert.assertEquals("gbk", result.getCharacterSetServer());
        }
    }

    /**
     * 测试混合场景：utf8mb3转换 + 大小写 + 无关变量
     */
    @Test
    public void testLoadCharactorSetFromCN_MixedScenario() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class)) {
            // Mock JdbcTemplate
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject("polarxJdbcTemplate"))
                .thenReturn(jdbcTemplate);

            // 准备测试数据 - 混合场景
            List<Pair<String, String>> mockResult = Arrays.asList(
                Pair.of("CHARACTER_SET_CLIENT", "UTF8MB3"), // 大写的utf8mb3
                Pair.of("character_set_results", "utf8mb4"), // 无关变量
                Pair.of("Character_Set_Connection", "utf8mb4"),
                Pair.of("character_set_system", "utf8"), // 无关变量
                Pair.of("character_set_database", "utf8mb3") // 小写的utf8mb3
            );

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockResult);

            // 执行测试
            ServerCharactorSet result = ServerCharactorSet.loadCharactorSetFromCN();

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals("utf8", result.getCharacterSetClient()); // utf8mb3 -> utf8
            Assert.assertEquals("utf8mb4", result.getCharacterSetConnection());
            Assert.assertEquals("utf8", result.getCharacterSetDatabase()); // utf8mb3 -> utf8
            Assert.assertEquals("utf8", result.getCharacterSetServer()); // 保持默认值
        }
    }
}
