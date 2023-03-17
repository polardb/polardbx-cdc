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

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * 搜索位点测试用例
 * 1 、 搜索CdcStart
 * 2 、 搜索指定TSO
 */
public class BinlogSearchPositionTest extends BinlogGenerateUtil {

    /**
     * 生成普通CdcStart序列
     */
    @Test
    public void testSearchTSO1() throws Exception {
        // t1(1) , t2(2),  t3(3) , t4(4)
        writeMagicCode();
        generateFDE();
        Heartbeat h1 = new Heartbeat();
        h1.prepare();
        localTransaction();
        h1.commit();
        Heartbeat h2 = new Heartbeat();
        h2.prepare();
        localTransaction();
        Heartbeat tsoH = new Heartbeat();

        tsoH.prepare();

        h2.commit();
        localTransaction();
        Heartbeat h3 = new Heartbeat();
        h3.prepare();
        localTransaction();

        tsoH.preCommit();
        h3.commit();
        tsoH.commit();

        Heartbeat h4 = new Heartbeat();
        h4.prepare();
        h4.commit();

        generateRotate("mysql-bin.00002");

        BinlogPosition targetPosition = search(tsoH.getTso());
        Assert.assertNotNull(targetPosition);
        Assert.assertEquals(tsoH.getTso(), targetPosition.getTso());
        Assert.assertTrue("expect:" + tsoH.getBegin() + ", actul: " + targetPosition.getPosition(),
            tsoH.getBegin() >= targetPosition.getPosition());
    }

    /**
     * 生成 CdcStart测试搜索
     */
    @Test
    public void searchCmd() throws Exception {
        writeMagicCode();
        generateFDE();
        Heartbeat h1 = new Heartbeat();
        h1.prepare();

        h1.commit();
        Heartbeat h2 = new Heartbeat();
        h2.prepare();
        CdcStart cdcStart = new CdcStart();
        cdcStart.prepare();

        h2.commit();

        Heartbeat h3 = new Heartbeat();
        h3.prepare();

        h3.commit();
        cdcStart.commit();

        Heartbeat h4 = new Heartbeat();
        h4.prepare();
        h4.commit();

        generateRotate("mysql-bin.00002");
        BinlogPosition targetPos = search(-1);
        Assert.assertNotNull(targetPos);
        Assert.assertEquals(cdcStart.getTso(), targetPos.getTso());
    }

}
