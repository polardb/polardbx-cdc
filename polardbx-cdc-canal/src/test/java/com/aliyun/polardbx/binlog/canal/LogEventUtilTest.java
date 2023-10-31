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
package com.aliyun.polardbx.binlog.canal;

import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * created by ziyang.lb
 **/
public class LogEventUtilTest {

    @Test
    @Ignore
    public void testCompareSplit() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            String xid = toXidString(System.currentTimeMillis(), "GROUP_XXX_YYY_" + i, System.currentTimeMillis());
            list.add(xid);
        }

        //
        long start = System.currentTimeMillis();
        for (String s : list) {
            s.split(",");
        }
        long end = System.currentTimeMillis();
        System.out.println(end - start);

        //
        start = System.currentTimeMillis();
        for (String s : list) {
            StringUtils.split(s, ",");
        }
        end = System.currentTimeMillis();
        System.out.println(end - start);

        //
        start = System.currentTimeMillis();
        for (String s : list) {
            StringUtils.substringBefore(s, ",");
        }
        end = System.currentTimeMillis();
        System.out.println(end - start);

        //
        start = System.currentTimeMillis();
        for (String s : list) {
            StringUtils.substringBefore(s, ",");
            s = StringUtils.substringAfter(s, ",");
            StringUtils.substringBefore(s, ",");
        }
        end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    @Test
    public void testGetGroupId() throws Exception {
        String xid1 =
            "X'647264732d313639633461343764303031    303030304033663966626466653165633638323563',X'5149454b4a5f50415944425f3030303031355f47524f55504030303030',1";
        String xid2 =
            "X'647264732d313464343866633434313030313030304035633337666238343537303130653631',X'544553545f445244535f3030303030345f47524f55504030303033',1";
        String group1 = LogEventUtil.getGroupFromXid(xid1, "UTF-8");
        String group2 = LogEventUtil.getGroupFromXid(xid2, "UTF-8");
        System.out.println(group1);
    }

    @Test
    public void testGetTxnId() throws Exception {
        String xid =
            "X'647264732d313632366632663237346330313030304062623236613963383163636433386265',X'5f5f4344435f5f5f3030303030305f47524f55504030303030',1";
        long txnId = LogEventUtil.getTranIdFromXid(xid, "utf-8");
        String txnId2 = Long.toHexString(txnId);
        System.out.println(txnId);
        return;
    }

    @Test
    public void testValidXid() {
        String xid =
            "X'504f4c415244422d582d5245434f5645522d5441534b4031356534353366646234383031303030',X'6e6f726d616c2d636f6d6d69742d32',2";
        System.out.println(LogEventUtil.isValidXid(xid));
    }

    public String toXidString(long transId, String group, long primaryGroupUid) {
        String xid = String.format("'drds-%s@%s', '%s'", Long.toHexString(transId),
            Long.toHexString(primaryGroupUid), group);
        return xid;
    }

    @Test
    public void testGetTranIdFromXid() throws Exception {
        String xid =
            "X'647264732d313632343862356465643030313030304065333934646132666561323064613231',X'445244535f504f4c415258315f5141544553545f4150505f53494e474c455f47524f55504030303134',1";
        String encoding = "utf8";
        System.out.println(LogEventUtil.getTranIdFromXid(xid, encoding));
        System.out.println(LogEventUtil.getGroupFromXid(xid, encoding));
    }

    @Test
    public void testGetHexTranIdFromXid() throws Exception {
        String xid =
            "X'647264732d313632343862356465643030313030304065333934646132666561323064613231',X'445244535f504f4c415258315f5141544553545f4150505f53494e474c455f47524f55504030303134',1";
        String encoding = "utf8";
        System.out.println(LogEventUtil.getHexTranIdFromXid(xid, encoding));
    }

    @Test
    public void testParseTid() throws Exception {
        String xid =
            "X'647264732d313261373530636132353030383030324062623236613963383163636433386265',X'5f5f4344435f5f5f3030303030325f47524f5550',1";
        Long tid = LogEventUtil.getTranIdFromXid(xid, "utf8");
        String groupName = LogEventUtil.getGroupFromXid(xid, "utf8");
        System.out.println("tid : " + tid + " , groupName : " + groupName);
    }
}
