/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    private static final String URL
        = "curl 'http://rds-api-internal.control.internal.rds.aliyuncs"
        + ".com:8087/services?Action=UpgradeCDCVersion&dbinstancename=%s&uid=%s&user_id=%s&engine=polarx"
        + "&opsserviceversion=2.0&cdcminorversion=polarx-cdc-20210702&cdcdbversion=2.343'";

    @Test
    public void test() {
        String s = "pxc-hzrmg6cqv0vv9y-cdc\tpolarx-cdc-20210610\t26842_1462796375976215\thaomai1010\t2\t疏光\n"
            + "pxc-szsp6m9s8mm4s0-cdc\tpolarx-cdc-20210427\t26842_1406926474064770\tinfra-doc\t2\t\n"
            + "pxc-hzrmlzvy6r222c-cdc\tpolarx-cdc-20210427\t26842_1788966360195792\tlogdb_platform\t2\t\n"
            + "pxc-shrjt0rb3ea9x5-cdc\tpolarx-cdc-20210610\t26842_1694785735938178\ttechservice07\t2\t\n"
            + "pxc-qdr7iokcnnh57n-cdc\tpolarx-cdc-20210610\t26842_1818405467403056\ttx_platform\t2\t\n"
            + "pxc-bjrtryd57jghsd-cdc\tpolarx-cdc-20210521\t26842_1664015389325723\t寅诺项目一\t0\t\n"
            + "pxc-bjrijruo408an9-cdc\tpolarx-cdc-20210521\t26842_1664015389325723\t寅诺项目一\t0\t\n"
            + "pxc-hzrd5axr8405rp-cdc\tpolarx-cdc-20210610\t26842_1817011232256314\tuser_experience\t0\t";

        String[] data = s.split("\n");

        for (int i = 0; i < data.length; i++) {
            String line = data[i];
            String[] ss = StringUtils.split(line, "\t", 4);
            String instId = StringUtils.removeEnd(ss[0], "-cdc");
            String[] bid_uid = StringUtils.split(ss[2], "_");

            String bid = bid_uid[0];
            String uid = bid_uid[1];
            System.out.println(String.format(URL, instId, uid, bid));
        }
    }
}
