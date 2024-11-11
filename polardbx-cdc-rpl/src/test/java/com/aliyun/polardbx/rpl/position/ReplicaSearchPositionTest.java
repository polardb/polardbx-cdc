/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.position;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.format.utils.ErosaConnectionAdapter;
import com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil;
import com.aliyun.polardbx.rpl.extractor.search.PositionFinder;
import com.aliyun.polardbx.rpl.extractor.search.handler.PositionSearchHandler;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class ReplicaSearchPositionTest {

    private BinlogGenerateUtil.Ddl createDatabaseddl;
    private BinlogGenerateUtil.TableDefine tableDefine;
    private BinlogGenerateUtil.Transaction txc;
    private BinlogGenerateUtil.Transaction txc2;
    private BinlogGenerateUtil.Transaction txc3;

    private BinlogGenerateUtil.Cts cts1;
    private BinlogGenerateUtil.Cts cts2;
    private BinlogGenerateUtil.Cts cts3;

    private long rotateOffset;

    private BinlogGenerateUtil prepare() throws Exception {
        BinlogGenerateUtil generateUtil = new BinlogGenerateUtil(false);
        createDatabaseddl = generateUtil.createDatabase("test");
        tableDefine =
            generateUtil.defineTableWithName("test", "t_db", "id", "bigint", "name", "varchar(20)");
        txc = generateUtil.newTransaction("test");
        txc.addTableData("t_db", "3", "测试3");
        txc.addTableData("t_db", "1", "测试1");
        txc.commit();
        cts1 = generateUtil.writeCTS(txc.getTranId());
        txc2 = generateUtil.newTransaction("test");
        txc2.addTableData("t_db", "3", "测试3");
        txc2.addTableData("t_db", "1", "测试1");
        txc2.commit();
        cts2 = generateUtil.writeCTS(txc2.getTranId());

        txc3 = generateUtil.newTransaction("test");
        txc3.addTableData("t_db", "3", "测试3");
        txc3.addTableData("t_db", "1", "测试1");
        txc3.commit();
        cts3 = generateUtil.writeCTS(txc3.getTranId());

        rotateOffset = generateUtil.generateRotate("binlog.01");
        return generateUtil;
    }

    @Test
    public void testSecondTxc() throws Exception {

        BinlogGenerateUtil generateUtil = prepare();

        BinlogPosition entryPosition = new BinlogPosition("binlog.01", "");
        entryPosition.setPosition(txc2.getBegin());
        BinlogPosition targetPos = search(generateUtil, entryPosition);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(cts2.getTso(), targetPos.getRtso());
    }

    @Test
    public void testFirstTxc() throws Exception {

        BinlogGenerateUtil generateUtil = prepare();

        BinlogPosition entryPosition = new BinlogPosition("binlog.01", "");
        entryPosition.setPosition(txc.getBegin());
        BinlogPosition targetPos = search(generateUtil, entryPosition);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(cts1.getTso(), targetPos.getRtso());
    }

    @Test
    public void testFirstDdlPosition() throws Exception {

        BinlogGenerateUtil generateUtil = prepare();

        BinlogPosition entryPosition = new BinlogPosition("binlog.01", "");
        entryPosition.setPosition(createDatabaseddl.getOffset());
        BinlogPosition targetPos = search(generateUtil, entryPosition);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(createDatabaseddl.getTso(), targetPos.getRtso());
    }

    @Test
    public void testRotatePosition() throws Exception {

        BinlogGenerateUtil generateUtil = prepare();

        BinlogPosition entryPosition = new BinlogPosition("binlog.01", "");
        entryPosition.setPosition(rotateOffset);
        BinlogPosition targetPos = search(generateUtil, entryPosition);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(cts3.getTso(), targetPos.getRtso());
    }

    private BinlogPosition search(BinlogGenerateUtil generateUtil, BinlogPosition entryPosition)
        throws IOException, TableIdNotFoundException {
        LogFetcher logFetcher =
            generateUtil.logFetcher();
        PositionFinder positionFinder = new PositionFinder(entryPosition, null,
            new PositionSearchHandler(entryPosition), new ErosaConnectionAdapter());
        positionFinder.setPolarx(true);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.01", 4));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        while (logFetcher.fetch()) {

            LogEvent event = decoder.decode(logFetcher, logContext);
            if (event == null) {
                continue;
            }

            if (!positionFinder.sink(event, logContext.getLogPosition())) {
                break;
            }
        }
        return positionFinder.findPos();
    }
}
