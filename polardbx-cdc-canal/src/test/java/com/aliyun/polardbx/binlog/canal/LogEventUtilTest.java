/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
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
            "X'647264732d313464343866633434313030313030304035633337666238343537303130653631',X'544553545f445244535f3030303030335f47524f55504030303032',1";
        String xid2 =
            "X'647264732d313464343866633434313030313030304035633337666238343537303130653631',X'544553545f445244535f3030303030345f47524f55504030303033',1";
        Long group1 = LogEventUtil.getTranIdFromXid(xid1, "UTF-8");
        Long group2 = LogEventUtil.getTranIdFromXid(xid2, "UTF-8");
        System.out.println(group1);
        System.out.println(group2);
    }

    @Test
    public void testGetTxnId() throws Exception {
        String xid =
            "X'647264732d313531373237333465383430313030314063313636376230393437623738653365',X'445244535f504f4c415258315f504152545f5141544553545f4150505f5030303030305f47524f55504030303030',1";
        long txnId = LogEventUtil.getTranIdFromXid(xid, "utf-8");
        System.out.println(txnId);
    }

    public String toXidString(long transId, String group, long primaryGroupUid) {
        String xid = String.format("'drds-%s@%s', '%s'", Long.toHexString(transId),
            Long.toHexString(primaryGroupUid), group);
        return xid;
    }
}
