/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ISearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV2;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil;
import com.aliyun.polardbx.binlog.format.utils.generator.CdcGenerateUtil;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 搜索位点测试用例
 * 1 、 搜索CdcStart
 * 2 、 搜索指定TSO
 */
public class BinlogSearchPositionTest extends BaseTestWithGmsTables {

    @Before
    public void before() {
        setConfig(ConfigKeys.CLUSTER_ID, "fake_cluster_id");
        setConfig(ConfigKeys.CLUSTER_TYPE, ClusterType.BINLOG.name());
        setConfig(ConfigKeys.CLUSTER_ROLE, ClusterRole.master.name());
    }

    /**
     * 生成普通CdcStart序列
     */
    @Test
    public void testSearchTSO1() throws Exception {
        CdcGenerateUtil generateUtil = new CdcGenerateUtil(false);
        // t1(1) , t2(2),  t3(3) , t4(4)
        CdcGenerateUtil.Heartbeat h1 = generateUtil.newHeartbeat();
        h1.prepare();
        generateUtil.localTransaction();
        h1.commit();
        CdcGenerateUtil.Heartbeat h2 = generateUtil.newHeartbeat();
        h2.prepare();
        generateUtil.localTransaction();
        CdcGenerateUtil.Heartbeat tsoH = generateUtil.newHeartbeat();

        tsoH.prepare();

        h2.commit();
        generateUtil.localTransaction();
        CdcGenerateUtil.Heartbeat h3 = generateUtil.newHeartbeat();
        h3.prepare();
        generateUtil.localTransaction();

        tsoH.tryPreGenerateTso();
        h3.commit();
        tsoH.commit();

        CdcGenerateUtil.Heartbeat h4 = generateUtil.newHeartbeat();
        h4.prepare();
        h4.commit();

        generateUtil.generateRotate("mysql-bin.00002");

        BinlogPosition targetPosition = search(tsoH.getTso(), h1.getTso(), generateUtil);
        Assert.assertNotNull(targetPosition);
        Assert.assertEquals(buildRtso(h2), targetPosition.getRtso());
        Assert.assertTrue("expect:" + tsoH.getBegin() + ", actul: " + targetPosition.getPosition(),
            tsoH.getBegin() >= targetPosition.getPosition());
    }

    /**
     * 生成 CdcStart测试搜索
     */
    @Test
    public void searchCmd() throws Exception {
        CdcGenerateUtil generateUtil = new CdcGenerateUtil(false);
        CdcGenerateUtil.Heartbeat h1 = generateUtil.newHeartbeat();
        h1.prepare();

        h1.commit();
        CdcGenerateUtil.Heartbeat h2 = generateUtil.newHeartbeat();
        h2.prepare();
        CdcGenerateUtil.CdcStart cdcStart = generateUtil.newCdcStart();
        cdcStart.prepare();

        h2.commit();

        CdcGenerateUtil.Heartbeat h3 = generateUtil.newHeartbeat();
        h3.prepare();

        h3.commit();
        cdcStart.commit();

        CdcGenerateUtil.Heartbeat h4 = generateUtil.newHeartbeat();
        h4.prepare();
        h4.commit();

        generateUtil.generateRotate("mysql-bin.00002");
        BinlogPosition targetPos = search(-1, 0, generateUtil);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(buildRtso(cdcStart), targetPos.getRtso());
    }

    private String buildRtso(BinlogGenerateUtil.Transaction transaction) {
        return CommonUtils.generateTSO(transaction.getTso(),
            StringUtils.rightPad(transaction.getTranId() + "", 29, "0"), null);
    }

    public BinlogPosition search(long searchTSO, long baseTSO, BinlogGenerateUtil generateUtil) throws Exception {
        String binlogFileName = "mysql-bin.00001";
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(null, null, null, "utf8");
        ISearchTsoEventHandle searchTsoEventHandle =
            new SearchTsoEventHandleV2(authenticationInfo, searchTSO, baseTSO, false, null);
        searchTsoEventHandle.onStart();
        searchTsoEventHandle.setEndPosition(new BinlogPosition(binlogFileName, 1024 * 8, 0, 0));
        LogPosition logPosition = new LogPosition(binlogFileName, 4);
        LogFetcher fetcher = generateUtil.logFetcher();

        LogDecoder decoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext(FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_4_0_x);
        logContext.setLogPosition(new LogPosition("", 0));
        ServerCharactorSet serverCharactorSet = new ServerCharactorSet();
        serverCharactorSet.setCharacterSetServer("utf8");
        serverCharactorSet.setCharacterSetClient("utf8");
        serverCharactorSet.setCharacterSetConnection("utf8");
        serverCharactorSet.setCharacterSetDatabase("utf8");
        logContext.setServerCharactorSet(serverCharactorSet);

        while (fetcher.fetch()) {
            LogEvent le = decoder.decode(fetcher, logContext);
            searchTsoEventHandle.handle(le, logPosition);
            if (le instanceof RotateLogEvent) {
                System.out.println("occur end");
                break;
            }
            if (searchTsoEventHandle.searchResult() != null) {
                System.out.println("search result");
                break;
            }
        }
        return searchTsoEventHandle.searchResult();
    }

}
