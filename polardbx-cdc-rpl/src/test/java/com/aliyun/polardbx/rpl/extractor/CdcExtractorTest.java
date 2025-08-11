/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import io.grpc.ManagedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class CdcExtractorTest extends BaseTest {

    private MockedStatic<DriverManager> driverManagerMockedStatic;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, SQLException {
        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        MetaManagerTranProxy metaManagerTranProxy = Mockito.mock(MetaManagerTranProxy.class);

        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        registerSpringObject("metaManagerTranProxy", metaManagerTranProxy);
    }


    public MockedStatic<DriverManager> mockDriverManager() throws SQLException {
        MockedStatic<DriverManager> driverManagerMockedStatic = mockStatic(DriverManager.class);
        Connection conn = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        mockRs(statement, "select @@global.sql_mode", true, new Object[]{""});
        mockRs(statement, "select @@global.binlog_checksum", true, new Object[]{"CRC32"});
        mockRs(statement, "show variables like '%character%'", false, new Object[]{"character_set_database", "utf8"});
        mockRs(statement, "show variables like 'lower_case_table_names'", false, new Object[]{"master.0001", 1});
        mockRs(statement, "show binlog events limit 1", true, new Object[]{"master.0001", 4});
        mockRs(statement, "show master status", true, new Object[]{"master.0001", 4});
        mockRs(statement, "show variables like 'server_id'", false, new Object[]{"server_id", 1});
        mockRs(statement, "show variables like 'binlog_row_image'", true, new Object[]{"binlog_row_image", "FULL"});
        mockRs(statement, "show variables like 'binlog_format'", true, new Object[]{"binlog_format", "ROW"});
        mockRs(statement, "show variables like 'binlog_checksum'", true, new Object[]{"binlog_checksum", "NONE"});


        when(conn.createStatement()).thenReturn(statement);
        driverManagerMockedStatic.when(() -> DriverManager.getConnection(anyString(), any(Properties.class))).thenReturn(conn);
        return driverManagerMockedStatic;
    }

    @Test
    public void testInitHostInfo() {
        CdcExtractor extractor = new CdcExtractor(null, new HostInfo(), null, null);
        ServerInfoMapper serverInfoMapper = Mockito.mock(ServerInfoMapper.class);
        extractor.setServerInfoMapper(serverInfoMapper);

        List<ServerInfo> serverInfoList = new ArrayList<>();
        serverInfoList.add(new ServerInfo());
        serverInfoList.get(0).setIp("0.0.0.0");
        serverInfoList.get(0).setPort(1);

        when(serverInfoMapper.select(any(SelectDSLCompleter.class))).thenReturn(serverInfoList);

        extractor.initHostInfo();

        assertEquals(extractor.getHostInfo().getHost(), "0.0.0.0");
        assertEquals(extractor.getHostInfo().getPort(), 1);
    }

    @Test
    public void initDumperInfo_StreamNameExists_XStreamExists_Success() {
        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setStreamName("testStream");
        TaskContext.getInstance().setPhysicalMeta(physicalMeta);
        CdcExtractor cdcExtractor = new CdcExtractor(null, new HostInfo("127.0.0.1", 3306, "root", "root", "", HostType.MYSQL, 1), null, null);
        XStreamMapper xStreamMapper = Mockito.mock(XStreamMapper.class);
        cdcExtractor.setXStreamMapper(xStreamMapper);

        XStream xStream = new XStream();
        xStream.setStreamName("testStream");
        xStream.setEndpoint("{\"host\":\"127.0.0.1\",\"port\":3306}");
        when(xStreamMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(xStream));

        cdcExtractor.initDumperInfo();

        assertEquals("127.0.0.1", cdcExtractor.getCdcServerIp());
        assertEquals(Integer.valueOf(3306), cdcExtractor.getCdcPort());
    }

    @Test
    public void initDumperInfo_StreamNameEmpty_DumperInfoExists_Success() {
        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setStreamName("");
        TaskContext.getInstance().setPhysicalMeta(physicalMeta);
        CdcExtractor cdcExtractor = new CdcExtractor(null, new HostInfo("127.0.0.1", 3306, "root", "root", "", HostType.MYSQL, 1), null, null);
        DumperInfoMapper dumperInfoMapper = Mockito.mock(DumperInfoMapper.class);
        cdcExtractor.setDumperInfoMapper(dumperInfoMapper);

        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setIp("127.0.0.2");
        dumperInfo.setPort(3307);
        when(dumperInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(dumperInfo));

        cdcExtractor.initDumperInfo();

        assertEquals("127.0.0.2", cdcExtractor.getCdcServerIp());
        assertEquals(Integer.valueOf(3307), cdcExtractor.getCdcPort());
    }

    @Test
    public void provideLogBuffer_PositionIsNullAndStreamNameIsNotBlank_ShouldUseStreamName()
        throws InterruptedException, IOException {
        BinlogPosition position = new BinlogPosition("file1", 100, -1, -1);
        try (MockedStatic<FSMMetaManager> fSMMetaManagerMockedStatic = mockStatic(FSMMetaManager.class)) {
            fSMMetaManagerMockedStatic.when(() -> FSMMetaManager.findStreamStartPosition("stream1"))
                .thenReturn(position);
            CdcExtractor cdcExtractor = Mockito.mock(CdcExtractor.class, withSettings().useConstructor(null, new HostInfo("127.0.0.1", 3306, "root", "root", "", HostType.MYSQL, 1), null, null));
            ManagedChannel channel = mock(ManagedChannel.class);
            CdcServiceGrpc.CdcServiceStub cdcServiceStub = Mockito.mock(CdcServiceGrpc.CdcServiceStub.class);
            doCallRealMethod().when(cdcExtractor).setChannel(channel);
            doCallRealMethod().when(cdcExtractor).setStreamName(anyString());
            doCallRealMethod().when(cdcExtractor).setCdcServiceStub(cdcServiceStub);
            when(cdcExtractor.provideLogBuffer()).thenCallRealMethod();
            cdcExtractor.setStreamName("stream1");
            cdcExtractor.setChannel(channel);
            cdcExtractor.setCdcServiceStub(cdcServiceStub);
            cdcExtractor.provideLogBuffer();
            verify(cdcServiceStub).dump(argThat(request -> request.getStreamName().equals("stream1")), any());
        }
    }

    @Test
    public void provideLogBuffer_PositionIsNullAndStreamNameIsBlank_ShouldNotUseStreamName()
        throws InterruptedException, IOException {
        BinlogPosition position = new BinlogPosition("file1", 100, -1, -1);
        try (MockedStatic<FSMMetaManager> fSMMetaManagerMockedStatic = mockStatic(FSMMetaManager.class)) {
            CdcExtractor cdcExtractor = Mockito.mock(CdcExtractor.class, withSettings().useConstructor(null, new HostInfo("127.0.0.1", 3306, "root", "root", "", HostType.MYSQL, 1), null, null));
            ManagedChannel channel = mock(ManagedChannel.class);
            CdcServiceGrpc.CdcServiceStub cdcServiceStub = Mockito.mock(CdcServiceGrpc.CdcServiceStub.class);
            doCallRealMethod().when(cdcExtractor).setChannel(channel);
            doCallRealMethod().when(cdcExtractor).setCdcServiceStub(cdcServiceStub);
            when(cdcExtractor.provideLogBuffer()).thenCallRealMethod();
            cdcExtractor.setStreamName("");
            cdcExtractor.setChannel(channel);
            cdcExtractor.setCdcServiceStub(cdcServiceStub);
            fSMMetaManagerMockedStatic.when(() -> FSMMetaManager.findStartPosition(channel)).thenReturn(position);
            cdcExtractor.provideLogBuffer();

            verify(cdcServiceStub).dump(argThat(request -> request.getStreamName().isEmpty()), any());
            verify(cdcServiceStub).dump(argThat(request -> request.getFileName().equals("file1")), any());
        }
    }

    private static void mockRs(Statement statement, String querySql, boolean isString, Object[] values) throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        if (isString){
            for (int i = 0; i < values.length; i++){
                when(rs.getString(i+1)).thenReturn(String.valueOf(values[i]));
            }
        }else{
            for (int i = 0; i < values.length; i++){
                when(rs.getObject(i+1)).thenReturn(values[i]);
            }
        }
        when(statement.executeQuery(querySql)).thenReturn(rs);
    }

    @Test
    public void testChecksumContext() throws Exception {
        try(MockedStatic<DriverManager> driverManagerMockedStatic1 = mockDriverManager()){
            ExtractorConfig extractorConfig = new ExtractorConfig();
            HostInfo hostInfo = new HostInfo();
            hostInfo.setHost("127.0.0.1");
            hostInfo.setPort(3306);
            hostInfo.setUserName("aa");
            hostInfo.setPassword("aa");
            BinlogPosition position = new BinlogPosition("master.0001", 4L, -1L, -1L);
            CdcExtractor extractor = Mockito.mock(CdcExtractor.class, withSettings().useConstructor(extractorConfig, hostInfo, null ,position));
            Mockito.when(extractor.providerLogContext()).thenCallRealMethod();
            Mockito.doCallRealMethod().when(extractor).initCharset();
            Mockito.when(extractor.isCrc32()).thenCallRealMethod();
            extractor.initCharset();
            LogContext context = extractor.providerLogContext();
            Assert.assertEquals(4, context.getFormatDescription().getBinlogVersion());
            Assert.assertEquals(LogEvent.BINLOG_CHECKSUM_ALG_CRC32, context.getFormatDescription().getHeader().getChecksumAlg());

            StreamObserverLogFetcher logFetcher = Mockito.mock(StreamObserverLogFetcher.class);
            Mockito.when(extractor.providerLogFetcher()).thenReturn(logFetcher);
            Mockito.doCallRealMethod().when(extractor).provideLogBuffer();
            CdcServiceGrpc.CdcServiceStub cdcServiceStub = Mockito.mock(CdcServiceGrpc.CdcServiceStub.class);
            Mockito.doCallRealMethod().when(extractor).setCdcServiceStub(cdcServiceStub);
            extractor.setCdcServiceStub(cdcServiceStub);
            extractor.provideLogBuffer();
            Map<String, String> ext = new HashMap<>();
            ext.put("master_binlog_checksum", "CRC32");
            DumpRequest request = DumpRequest.newBuilder()
                .setFileName(position.getFileName())
                .setExt(JSON.toJSONString(ext))
                .setPosition(4).build();
            verify(cdcServiceStub, times(1)).dump(request, logFetcher);
        }
    }

}
