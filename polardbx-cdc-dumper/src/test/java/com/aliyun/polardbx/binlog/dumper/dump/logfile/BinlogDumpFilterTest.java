/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.protobuf.ByteString;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public class BinlogDumpFilterTest extends BaseTest {
    private BinlogDumpFilter binlogDumpFilter;

    @Test
    public void testIgnoreArchive() {
        /*
            #250416 16:07:33 server id 1  end_log_pos 677969 CRC32 0xf417b89c       Rows_query
            # # CTS::731818325516799193618492484595010764810000000004213466::ARCHIVE
         */
        String hex =
            "4565ff671d010000005e00000051580a0000000123204354533a3a3733313831383332353531363739393139333631383439323438343539353031303736343831303030303030303030343231333436363a3a415243484956459cb817f4";
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.archiveIgnoreEnabled = true;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(hex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        Assert.assertTrue(result);
    }

    @Test
    public void testIgnoreTable() {
        /*
           #250416 16:07:33 server id 1  end_log_pos 678029 CRC32 0xd814da3d       Table_map: `test_ttl`.`test_ttl` mapped to number 1
         */
        String tableMap =
            "4565ff6713010000003c0000008d580a000000010000000000010008746573745f74746c0008746573745f74746c0003030f1203000100063dda14d8";
        Set<String> set = new HashSet<>(1);
        set.add("test_ttl.test_ttl");
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.tableSet = set;
        vars.whiteListMode = false;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(tableMap);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        Assert.assertTrue(result);
        /*
            #250416 16:07:33 server id 1  end_log_pos 678077 CRC32 0xb1d3040c       Delete_rows: table id 1 flags: STMT_END_F NO_FOREIGN_KEY_CHECKS_F
         */
        String deleteRows =
            "4565ff67200100000030000000bd580a0000000100000000000300020003fff80100000001006198d578de130c04d3b1";
        data = ByteBufUtil.decodeHexDump(deleteRows);
        result = binlogDumpFilter.filter(new ByteArray(data));
        Assert.assertTrue(result);

    }

    @Test
    public void testIgnoreRowsQuery() {
        /*
            #250416 16:07:33 server id 1  end_log_pos 677969 CRC32 0xf417b89c       Rows_query
            # # CTS::731818325516799193618492484595010764810000000004213466::ARCHIVE
         */
        String hex =
            "4565ff671d010000005e00000051580a0000000123204354533a3a3733313831383332353531363739393139333631383439323438343539353031303736343831303030303030303030343231333436363a3a415243484956459cb817f4";
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.rowsQueryIgnoreEnabled = true;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(hex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        Assert.assertTrue(result);
    }

    @Test
    public void testIgnoreServer() {
        /*
            #250416 16:07:33 server id 1  end_log_pos 677969 CRC32 0xf417b89c       Rows_query
            # # CTS::731818325516799193618492484595010764810000000004213466::ARCHIVE
         */
        String hex =
            "4565ff671d010000005e00000051580a0000000123204354533a3a3733313831383332353531363739393139333631383439323438343539353031303736343831303030303030303030343231333436363a3a415243484956459cb817f4";
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_IGNORE_ENABLED, "true");
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreServerIds = "1,2";
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(hex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        Assert.assertTrue(result);
    }

    @Test
    public void testServerIdFilterDdl_ddlNotFiltered() {
        // Real DDL QUERY_EVENT from MySQL 8.0: server_id=1627, query="CREATE TABLE t1 (id INT PRIMARY KEY)"
        // Extracted from binlog.000190 at position 713, size=138 bytes
        String ddlHex =
            "cb39436a025b0600008a00000053030000000014000000000000000b000036000000000000012000a04500000000060373746404ff00ff00ff000c01746573745f66696c74657200111e0000000000000012ff001300746573745f66696c74657200435245415445205441424c452074312028696420494e54205052494d415259204b45592961b3d055";
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_IGNORE_ENABLED, "true");
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_FILTER_DDL, "false");
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreServerIds = "1627";
        vars.serverIdFilterDdlEnabled = false;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(ddlHex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        // DDL should NOT be filtered
        Assert.assertFalse(result);
    }

    @Test
    public void testServerIdFilterDdl_beginStillFiltered() {
        // Real BEGIN QUERY_EVENT from MySQL 8.0: server_id=1627, query="BEGIN"
        // Extracted from binlog.000190 at position 930, size=82 bytes
        String beginHex =
            "cb39436a025b06000052000000f4030000080014000000000000000b00001d000000000000012000a04500000000060373746404ff00ff00ff0012ff00746573745f66696c74657200424547494e9db7cc4c";
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_IGNORE_ENABLED, "true");
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_FILTER_DDL, "false");
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreServerIds = "1627";
        vars.serverIdFilterDdlEnabled = false;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(beginHex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        // BEGIN should still be filtered
        Assert.assertTrue(result);
    }

    @Test
    public void testServerIdFilterDdl_backwardCompat() {
        // Real DDL QUERY_EVENT: same as ddlNotFiltered test but with serverIdFilterDdlEnabled=true
        String ddlHex =
            "cb39436a025b0600008a00000053030000000014000000000000000b000036000000000000012000a04500000000060373746404ff00ff00ff000c01746573745f66696c74657200111e0000000000000012ff001300746573745f66696c74657200435245415445205441424c452074312028696420494e54205052494d415259204b45592961b3d055";
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_IGNORE_ENABLED, "true");
        mockConfig(ConfigKeys.BINLOG_DUMP_SERVER_ID_FILTER_DDL, "true");
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreServerIds = "1627";
        vars.serverIdFilterDdlEnabled = true;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(ddlHex);
        boolean result = binlogDumpFilter.filter(new ByteArray(data));
        // DDL should be filtered (backward compat)
        Assert.assertTrue(result);
    }

    @Test
    @SneakyThrows
    public void testIgnoreRowsQueryBySetFlag() {
        /*
            #250416 16:07:33 server id 1  end_log_pos 677969 CRC32 0xf417b89c       Rows_query
            # # CTS::731818325516799193618492484595010764810000000004213466::ARCHIVE
         */
        String hex =
            "4565ff671d010000005e00000051580a0000000123204354533a3a3733313831383332353531363739393139333631383439323438343539353031303736343831303030303030303030343231333436363a3a415243484956459cb817f4";
        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreBySetFlag = true;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(hex);
        ByteString bs = binlogDumpFilter.getFilteredData(data, 0);
        bs.copyTo(data, 0);
        // LOG_EVENT_IGNORABLE_F is set
        Assert.assertTrue((data[17] & 0x80) != 0);

        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        LogEvent event = logDecoder.decode(new LogBuffer(data, 0, data.length), logContext);
        int flags = event.getHeader().getFlags();
        Assert.assertTrue((flags & 0x0080) != 0);
        Assert.assertEquals(0, event.getHeader().getType());
    }

    @Test
    @SneakyThrows
    public void testModifyFormatEvent() {
        /*
         * # at 4
           # 250428 16:08:46 server id 1  end_log_pos 123 CRC32 0x63b6e7ea  Start: binlog v 4, server v 5.6.29-TDDL-5.x created 250428 16:08:46 at startup
         */
        String hex =
            "8e370f680f01000000770000007b00000000000400352e362e32392d5444444c2d352e780000000000000000000000000000000000000000000000000000000000000000000000c9e60e6813000d0008000000000400040000005f00041a08000000080808020000000a0a0a2a2a0012340001eae7b663";

        BinlogDumpUserVariables vars = new BinlogDumpUserVariables();
        vars.ignoreBySetFlag = true;
        binlogDumpFilter = new BinlogDumpFilter(vars);
        byte[] data = ByteBufUtil.decodeHexDump(hex);
        binlogDumpFilter.filter(new ByteArray(data));

        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        LogEvent event = logDecoder.decode(new LogBuffer(data, 0, data.length), logContext);
        log.info("checksumAlg:{}", event.getHeader().getChecksumAlg());
        Assert.assertEquals(LogEvent.BINLOG_CHECKSUM_ALG_UNDEF, event.getHeader().getChecksumAlg());
    }
}
