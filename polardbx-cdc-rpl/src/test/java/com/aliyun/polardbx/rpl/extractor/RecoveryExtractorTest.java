/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.extractor.flashback.BinlogFileQueue;
import com.aliyun.polardbx.rpl.extractor.flashback.ILocalBinlogEventListener;
import com.aliyun.polardbx.rpl.extractor.flashback.LocalBinLogConnection;
import com.aliyun.polardbx.rpl.extractor.flashback.LocalBinlogEventParser;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Slf4j
@Ignore
public class RecoveryExtractorTest extends RplWithGmsTablesBaseTest {

    @Test
    public void binlogFileQueueTest() {
        List<String> binlogList = new ArrayList<>();
        binlogList.add("binlog.000001");
        binlogList.add("binlog.000002");
        binlogList.add("binlog.000003");
        binlogList.add("binlog.000004");

        String pathName = "/Users/fanfei/recoveryUnitTest";
        File dir = new File(pathName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (String fileName : binlogList) {
            File f = new File(pathName + File.separator + fileName);
            if (!f.exists()) {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    log.error("create new file error!");
                }
            }
        }

        BinlogFileQueue fileQueue = new BinlogFileQueue(pathName);
        fileQueue.setBinlogList(binlogList);

        File firstFile = fileQueue.getFirstFile();
        assertNotNull(firstFile);
        assertEquals(firstFile.getName(), binlogList.get(0));
        File secondFile = fileQueue.getNextFile(firstFile);
        assertNotNull(secondFile);
        assertEquals(secondFile.getName(), binlogList.get(1));
        firstFile = fileQueue.getBefore(secondFile);
        assertNotNull(firstFile);
        assertEquals(firstFile.getName(), binlogList.get(0));
        fileQueue.destroy();
        firstFile = fileQueue.getFirstFile();
        assertNull(firstFile);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            log.error("delete directory error");
        }
    }

    @Test
    public void localConnectionTest() {
        String localDirectory = "/Users/fanfei/recoveryUnitTest";
        List<String> binlogList = new LinkedList<>();
        binlogList.add("binlog.000001");
        binlogList.add("binlog.000002");
        binlogList.add("binlog.000003");

        LocalBinLogConnection connection = new LocalBinLogConnection(localDirectory, binlogList, false,
            new ILocalBinlogEventListener() {
                @Override
                public void onEnd() {
                    log.info("local connection on End.");
                }

                @Override
                public void onFinishFile(File binlogFile, BinlogPosition pos) {
                    log.info("finish file: " + binlogFile.getName());
                }

            }, -1);
        try {
            connection.connect();
            connection.dump(binlogList.get(0), 0L, null, new SinkFunction() {
                @Override
                public boolean sink(LogEvent event, LogPosition logPosition)
                    throws CanalParseException, TableIdNotFoundException {
                    log.info(event.info());
                    return true;
                }
            });
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    @Test
    public void localParserTest() throws Exception {
        List<String> binlogList = new LinkedList<>();
        binlogList.add("binlog.000001");
        binlogList.add("binlog.000002");
        binlogList.add("binlog.000003");

        LocalBinlogEventParser parser = new LocalBinlogEventParser(1024, false, new ILocalBinlogEventListener() {
            @Override
            public void onEnd() {
                log.info("local connection on End.");
            }

            @Override
            public void onFinishFile(File binlogFile, BinlogPosition pos) {
                log.info("finish file: " + binlogFile.getName());
            }
        }, false, new RplEventRepository(new PersistConfig()));
        parser.setBinlogList(binlogList);

        HostInfo hostInfo = createPolarXHostInfo();
        BaseFilter filter = new BaseFilter();
        LogEventConvert logEventConvert = new LogEventConvert(hostInfo, filter, null, HostType.POLARX2);
        logEventConvert.init();

        AuthenticationInfo srcAuthInfo = new AuthenticationInfo();
        srcAuthInfo.setAddress(new InetSocketAddress(hostInfo.getHost(), hostInfo.getPort()));
        srcAuthInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        srcAuthInfo.setUsername(hostInfo.getUserName());
        srcAuthInfo.setPassword(hostInfo.getPassword());

        parser.setBinlogParser(logEventConvert);
        parser.start(srcAuthInfo, null, new BinlogEventSink() {
            @Override
            public boolean sink(List<MySQLDBMSEvent> events) {
                for (MySQLDBMSEvent event : events) {
                    log.info(event.toString());
                }
                return true;
            }

            @Override
            public boolean sink(Throwable e) {
                return false;
            }
        });
        try {
            Thread.sleep(20000L);
        } catch (InterruptedException e) {
        }

    }

    private HostInfo createPolarXHostInfo() {
        String srcHost = "127.0.0.1";
        int port = 8527;
        String user = "polardbx_root";
        String passwd = "123456";
        String schema = "yudong_test";
        long serverId = 0;

        return new HostInfo(srcHost, port, user, passwd, schema, HostType.POLARX2, serverId);
    }

    @Test
    public void noTsoEventParserTest() throws Exception {
        MysqlEventParser parser = new MysqlEventParser(1024, new RplEventRepository(new PersistConfig()));

        HostInfo hostInfo = createPolarXHostInfo();
        BaseFilter filter = new BaseFilter();
        LogEventConvert logEventConvert = new LogEventConvert(hostInfo, filter, null, HostType.POLARX2);
        logEventConvert.init();
        parser.setBinlogParser(logEventConvert);

        AuthenticationInfo srcAuthInfo = new AuthenticationInfo();
        srcAuthInfo.setAddress(new InetSocketAddress(hostInfo.getHost(), hostInfo.getPort()));
        srcAuthInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        srcAuthInfo.setUsername(hostInfo.getUserName());
        srcAuthInfo.setPassword(hostInfo.getPassword());
        BinlogPosition startPosition = new BinlogPosition("binlog.000001", 0, -1, 0);
        parser.start(srcAuthInfo, startPosition, new BinlogEventSink() {
            @Override
            public boolean sink(List<MySQLDBMSEvent> events) {
                for (MySQLDBMSEvent event : events) {
                    log.info(event.toString());
                }
                return true;
            }

            @Override
            public boolean sink(Throwable e) {
                return false;
            }
        });
        try {
            Thread.sleep(200000L);
        } catch (InterruptedException e) {
        }
    }



}
