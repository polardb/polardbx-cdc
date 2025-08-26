/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.CanalBootstrap;
import com.aliyun.polardbx.binlog.canal.MySqlInfo;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.BinlogBuilder;
import com.aliyun.polardbx.binlog.format.GcnEventBuilder;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.RotateEventBuilder;
import com.aliyun.polardbx.binlog.format.XAPrepareEventBuilder;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class CanalSearchTest extends BaseTest {


    public BinlogPosition doSearch(String requestTso, String startCmdTSO, ErosaConnection connection, boolean quickSearch) throws Exception {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, quickSearch+"");
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setStorageMasterInstId("test-master-dn-id");
        CanalBootstrap canalBootstrap = new CanalBootstrap(authenticationInfo, "", "", 0L, startCmdTSO);
        MySqlInfo mySqlInfo = Mockito.mock(MySqlInfo.class);
        Mockito.when(mySqlInfo.getServerId()).thenReturn(1L);
        Mockito.when(mySqlInfo.getServerCharactorSet()).thenReturn(new ServerCharactorSet());
        Mockito.when(mySqlInfo.getBinlogChecksum()).thenReturn(LogEvent.BINLOG_CHECKSUM_ALG_OFF);
        canalBootstrap.searchTestInit(mySqlInfo);
        return canalBootstrap.searchPosition(connection, requestTso);
    }

    public BinlogPosition doSearch(String requestTso, String startCmdTSO, ErosaConnection connection) throws Exception {
        return doSearch(requestTso, startCmdTSO, connection, false);
    }

    /**
     * 测试本地文件列表搜索位点类
     * @throws Exception
     */
    @Test
    @Ignore
    public void testSearchLocalBinlogList() throws Exception {
        String requestTso = "730402791714560416018350931326774845440000000040498871";
        String startCmdTSO = "730402690874684217618350921222948167680000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("dn-1-mysql_bin.000001","dn-1-mysql_bin.000002","dn-1-mysql_bin.000003","dn-1-mysql_bin.000004"), "/Users/yanfenglin/Downloads/binlog/big_tran", false);
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long minPos = 522345352L;
        String fileName = "dn-1-mysql_bin.000002";
        System.out.println(binlogPosition);
        Assert.assertTrue(binlogPosition.getPosition() <= minPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
    }

    @Test
    public void testCompare(){
        BinlogPosition b1 = new BinlogPosition("dn-1-mysql_bin.000002", 185041385L, -1, -1);
        BinlogPosition b2 = new BinlogPosition("dn-1-mysql_bin.000002", 2500884469L, -1, -1);

        Assert.assertEquals(-1, b1.compareTo(b2));
    }
    /**
     * 跨文件空洞搜索
     * @throws Exception
     */
    @Test
    public void testLostXaInTwoFilesSearch() throws Exception {
        String requestTso = "730227104359750048018333362247573299200000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.001200", "mysql-bin.001201"),
            CanalSearchTest.class.getResource("/search_resource_test/tran_in_two_files").getPath(), true);
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long minPos = 522345352L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.001200";
        Assert.assertTrue(binlogPosition.getPosition() < minPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
    }

    /**
     * request tso 在文件开头
     * 下一个文件tso 全部小于 request tso
     * 末尾时，触发setFind测试
     * @throws Exception
     */
    @Test
    public void testLostXaInTwoFilesSearchAndPosAtHead() throws Exception {
        String requestTso = "730227102235754502418333362378561454080000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.001200", "mysql-bin.001201"),
            CanalSearchTest.class.getResource("/search_resource_test/pos_tran_at_head_of_files").getPath(), true);
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long minPos = 522345352L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.001200";
        Assert.assertTrue(binlogPosition.getPosition() < minPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
    }

    @Test
    public void testSearchScaleOutDn() throws Exception {
        String requestTso = "730227102235754502118333362378561454080000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.000001", "mysql-bin.000002"),
        CanalSearchTest.class.getResource("/search_resource_test/receive_create_cdc_db_pos").getPath(), true);
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        jdbcTemplate.execute("replace into db_group_info(gmt_created,gmt_modified,db_name,group_name,phy_db_name,group_type) values(NOW(), NOW(), '__cdc__', '__cdc__group_1', '__cdc__00000', 1);");
        jdbcTemplate.execute("replace into group_detail_info(gmt_created,gmt_modified,inst_id,db_name,group_name,storage_inst_id) values(NOW(), NOW(), 'test-dn', '__cdc__', '__cdc__group_1', 'test-master-dn-id');");
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long minPos = 222L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.000001";
        Assert.assertTrue(binlogPosition.getPosition() < minPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
    }

    @Test
    public void testSearchScaleOutDnWithBinarySearch() throws Exception {
        String requestTso = "730227102235754502118333362378561454080000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.000001", "mysql-bin.000002"),
            CanalSearchTest.class.getResource("/search_resource_test/receive_create_cdc_db_pos").getPath(), true);
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        jdbcTemplate.execute("replace into db_group_info(gmt_created,gmt_modified,db_name,group_name,phy_db_name,group_type) values(NOW(), NOW(), '__cdc__', '__cdc__group_1', '__cdc__00000', 1);");
        jdbcTemplate.execute("replace into group_detail_info(gmt_created,gmt_modified,inst_id,db_name,group_name,storage_inst_id) values(NOW(), NOW(), 'test-dn', '__cdc__', '__cdc__group_1', 'test-master-dn-id');");
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection, true);
        long minPos = 222L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.000001";
        Assert.assertTrue(binlogPosition.getPosition() < minPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
        Assert.assertEquals(requestTso, binlogPosition.getRtso());
    }

    /**
     * 正常无空洞事物搜索
     * @throws Exception
     */
    @Test
    public void testNormalSearch() throws Exception {
        String requestTso = "730227104402531948818333362247573300000000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.001200"),
            CanalSearchTest.class.getResource("/search_resource_test/simple_search").getPath(), true);
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long rightPos = 1280L;
        long leftPos = 660L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.001200";
        Assert.assertTrue(binlogPosition.getPosition() < rightPos);
        Assert.assertTrue(binlogPosition.getPosition() > leftPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
        Assert.assertTrue(binlogPosition.getRtso().startsWith(7302271021912948800L+""+1833336216360337408L) );
    }

    /**
     * 单个文件request tso 空洞问题搜索
     * 空洞事物包含 request tso 事物
     * @throws Exception
     */
    @Test
    public void testNormalSearchSingleFileSlot() throws Exception {
        String requestTso = "730227104402531948818333362247573300000000000001376012";
        String startCmdTSO = "729445071924599200018255159338972569600000000000000000";
        MyConnection connection = new MyConnection(Arrays.asList("mysql-bin.000001"),
            CanalSearchTest.class.getResource("/search_resource_test/single_file_search_slot").getPath(), true);
        BinlogPosition binlogPosition = doSearch(requestTso, startCmdTSO, connection);
        long rightPos = 1280L;
        // 返回的位点是nextLogPos - eventLength
        String fileName = "mysql-bin.000001";
        Assert.assertTrue(String.format("binlogPosition.getPosition()=%s < %s failed", binlogPosition.getPosition(), rightPos), binlogPosition.getPosition() < rightPos);
        Assert.assertEquals(fileName, binlogPosition.getFileName());
        Assert.assertTrue(binlogPosition.getRtso().startsWith(7302271021912948800L+""+1833336216360337408L) );
    }

    /**
     * #250303 18:12:27 server id 2937886217  end_log_pos 904 CRC32 1988185027 Query
     * XA COMMIT X'647264732d313937313530353235636334333030634063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1
     *
     * #250303 18:12:27 server id 2937886217  end_log_pos 1551 CRC32 647177358 Gcn
     * 7302269622491807872
     *
     * #250303 18:12:27 server id 2937886217  end_log_pos 3370 CRC32 526666635 XA_PREPARE
     * X'647264732d313937313530353235663034363030384063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1
     *
     * #250303 18:12:27 server id 2937886217  end_log_pos 3267 CRC32 3821508037        Query
     * XA END X'647264732d313937313530353235663034363030384063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1
     */
    public static class LocalEventMockFetcher extends LogFetcher{

        private static final int HEADER_TYPE_IDX = 9;
        private static final String HEADER_TYPE_QUERY = "Query";
        private static final String HEADER_TYPE_GCN = "Gcn";
        private static final String HEADER_TYPE_XA_PREPARE = "XA_PREPARE";
        private BufferedReader reader;
        private DataInputStream output;
        private long lastOffset;
        public void open(String fileName) throws IOException {
            reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(fileName)),
                StandardCharsets.UTF_8));
            String header = null;
            AutoExpandBuffer autoExpandBuffer = new AutoExpandBuffer(256, 64);
            AutoExpandBuffer totalBuffer = new AutoExpandBuffer(256, 64);
            long server_id = 0;
            long timestamp = 0;
            while ((header = reader.readLine())!=null){
                String data = reader.readLine();
                String[] headers = header.split("( )+");
                autoExpandBuffer.reset();
                try{
                    timestamp = new SimpleDateFormat("yyMMdd HH:mm:ss").parse(headers[0].substring(1)+" "+headers[1]).getTime();

                    server_id = Long.parseLong(headers[4]);
                    long end_log_pos = Long.parseLong(headers[6]);
                    lastOffset = end_log_pos;
                    BinlogBuilder builder = null;
                    if (Objects.equals(headers[HEADER_TYPE_IDX], HEADER_TYPE_QUERY)){
                        Integer clientCharsetId = CharsetConversion.getCharsetId("utf8");
                        Integer connectionCharsetId = CharsetConversion.getCharsetId("utf8");
                        Integer serverCharsetId = CharsetConversion.getCharsetId("utf8");
                        builder = new QueryEventBuilder("polardbx", data, clientCharsetId, connectionCharsetId, serverCharsetId, false, (int) (timestamp/1000), (int) server_id);

                    }else if (Objects.equals(headers[HEADER_TYPE_IDX], HEADER_TYPE_GCN)){
                        long tso = Long.parseLong(data);
                        builder = new GcnEventBuilder((int) (timestamp/1000), 4, (int) server_id, tso);
                    }else if (Objects.equals(headers[HEADER_TYPE_IDX], HEADER_TYPE_XA_PREPARE)){
                        byte[] array = data.getBytes(StandardCharsets.UTF_8);
                        builder = new XAPrepareEventBuilder((int) (timestamp/1000), (int) server_id, false, 1, 0, array.length, array);
                    }
                    if (builder == null){
                        continue;
                    }
                    builder.write(autoExpandBuffer);
                    byte[] finalData = new byte[autoExpandBuffer.size()];
                    autoExpandBuffer.writeTo(finalData);
                    EventGenerator.updatePos(finalData, end_log_pos);
                    totalBuffer.put(finalData);
                }catch (Exception e){
                    throw new PolardbxException("parser date failed! "+header, e);
                }
            }
            try{
                autoExpandBuffer.reset();
                RotateEventBuilder rotateEventBuilder = new RotateEventBuilder((int) (timestamp/1000), server_id,  "mysql-bin.1111111", 128);
                rotateEventBuilder.write(autoExpandBuffer);
                byte[] finalData = new byte[autoExpandBuffer.size()];
                autoExpandBuffer.writeTo(finalData);
                lastOffset += finalData.length;
                EventGenerator.updatePos(finalData, 500L*1024*1024);
                totalBuffer.put(finalData);
            }catch (Exception e){
                throw new PolardbxException("parser date failed! "+header, e);
            }


            byte[] finalData = new byte[totalBuffer.size()];
            totalBuffer.writeTo(finalData);
            output = new DataInputStream(new ByteArrayInputStream(finalData));

        }

        public long getLastOffset(){
            return  lastOffset;
        }

        public boolean fetch() throws IOException {
            if (limit == 0) {
                final int len = output.read(buffer, 0, buffer.length);
                if (len >= 0) {
                    limit += len;
                    position = 0;
                    origin = 0;

                    /* More binlog to fetch */
                    return true;
                }
            } else if (origin == 0) {
                if (limit > buffer.length / 2) {
                    ensureCapacity(buffer.length + limit);
                }
                final int len = output.read(buffer, limit, buffer.length - limit);
                if (len >= 0) {
                    limit += len;

                    /* More binlog to fetch */
                    return true;
                }
            } else if (limit > 0) {
                if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
                    int lenPosition = position + 4 + 1 + 4;
                    long eventLen = ((long) (0xff & buffer[lenPosition++])) | ((long) (0xff & buffer[lenPosition++]) << 8)
                        | ((long) (0xff & buffer[lenPosition++]) << 16)
                        | ((long) (0xff & buffer[lenPosition++]) << 24);

                    if (limit >= eventLen) {
                        return true;
                    } else {
                        ensureCapacity((int) eventLen);
                    }
                }

                System.arraycopy(buffer, origin, buffer, 0, limit);
                position -= origin;
                origin = 0;
                final int len = output.read(buffer, limit, buffer.length - limit);
                if (len >= 0) {
                    limit += len;

                    /* More binlog to fetch */
                    return true;
                }
            } else {
                /* Should not happen. */
                throw new IllegalArgumentException("Unexcepted limit: " + limit);
            }

            /* Reach binlog file end */
            return false;
        }

        @Override
        public void close() throws IOException {
            if (reader != null){
                reader.close();
            }
        }
    }

    public static class MyConnection implements ErosaConnection{

        private List<String> binlogList = new ArrayList<>();
        private String path;
        private boolean forTest;
        private long size;

        public MyConnection(List<String> binlogList, String path, boolean forTest) {
            this.binlogList = binlogList;
            this.path = path;
            this.forTest = forTest;
        }


        @Override
        public void connect() throws IOException {

        }

        @Override
        public void reconnect() throws IOException {

        }

        @Override
        public void disconnect() throws IOException {

        }

        @Override
        public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {

        }

        @Override
        public void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills, SinkFunction func)
            throws Exception {

        }

        @Override
        public void dump(long timestamp, SinkFunction func) throws Exception {

        }

        @Override
        public void dump(GTIDSet gtidSet, SinkFunction func) throws Exception {

        }

        @Override
        public ErosaConnection fork() {
            return this;
        }

        @Override
        public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search)
            throws IOException {
            if (forTest){
                LocalEventMockFetcher fetcher = new LocalEventMockFetcher();
                fetcher.open(path+ File.separator+binlogfilename);
                size = fetcher.getLastOffset();
                return fetcher;
            }else {
                FileLogFetcher fetcher = new FileLogFetcher(8192);
                fetcher.open(path+ File.separator+binlogfilename);
                size = new File(path+ File.separator+binlogfilename).length();
                return fetcher;
            }
        }

        @Override
        public BinlogPosition findEndPosition(Long tso) {
            return new BinlogPosition(binlogList.get(binlogList.size()-1), new File(path+ File.separator+binlogList.get(binlogList.size()-1)).length(), -1, -1);
        }

        @Override
        public long binlogFileSize(String searchFileName) throws IOException {
            return size;
        }

        @Override
        public String preFileName(String currentFileName) {
            if (binlogList.contains(currentFileName)) {
                int index = binlogList.indexOf(currentFileName);
                if (index > 0) {
                    return binlogList.get(index - 1);
                }
            }
            return null;
        }

        @Override
        public List<String> binlogList() {
            return new CopyOnWriteArrayList<>(binlogList);
        }
    }
}
