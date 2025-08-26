/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorType;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

public class SerialPipelineTest {

    private SerialPipeline serialPipeline;
    private PipelineConfig pipelineConfig;
    private ExtractorConfig extractorConfig = new ExtractorConfig();
    private BaseExtractor extractor = new BaseExtractor();

    @Test
    public void initBufferSize_BufferSizeAlreadySet_NoChange() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_RINGBUFFER_SIZE)).thenReturn(4096);
            pipelineConfig = new PipelineConfig();
            extractorConfig.setExtractorType(ExtractorType.RPL_FULL);
            extractor.setExtractorConfig(extractorConfig);
            pipelineConfig.setBufferSize(2048);
            serialPipeline = new SerialPipeline(pipelineConfig, extractor, null);
            serialPipeline.initBufferSize();
            Assert.assertEquals(2048, pipelineConfig.getBufferSize());
        }
    }

    @Test
    public void initBufferSize_MemoryInMbSet_CalculatesBufferSize() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getDouble(ConfigKeys.RPL_INC_RINGBUFFER_SIZE_FACTOR)).thenReturn(300.0);
            System.setProperty(ConfigKeys.MEMORY_IN_MB, "3");
            pipelineConfig = new PipelineConfig();
            extractorConfig.setExtractorType(ExtractorType.RPL_FULL);
            extractor.setExtractorConfig(extractorConfig);
            pipelineConfig.setBufferSize(0);
            serialPipeline = new SerialPipeline(pipelineConfig, extractor, null);
            serialPipeline.initBufferSize();
            System.clearProperty(ConfigKeys.MEMORY_IN_MB);
            Assert.assertEquals(1024, pipelineConfig.getBufferSize());
        }
    }

    @Test
    public void initBufferSize_MemoryInMbNotSet_DefaultBufferSize() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getDouble(ConfigKeys.RPL_INC_RINGBUFFER_SIZE_FACTOR)).thenReturn(300.0);
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_RINGBUFFER_SIZE)).thenReturn(256);
            pipelineConfig = new PipelineConfig();
            extractorConfig.setExtractorType(ExtractorType.RPL_FULL);
            extractor.setExtractorConfig(extractorConfig);
            pipelineConfig.setBufferSize(0);
            serialPipeline = new SerialPipeline(pipelineConfig, extractor, null);
            serialPipeline.initBufferSize();
            Assert.assertEquals(256, pipelineConfig.getBufferSize());
        }
    }
}
