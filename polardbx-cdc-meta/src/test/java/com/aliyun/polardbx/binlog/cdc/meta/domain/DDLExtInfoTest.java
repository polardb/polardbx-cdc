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
package com.aliyun.polardbx.binlog.cdc.meta.domain;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

/**
 * created by ziyang.lb
 **/
public class DDLExtInfoTest extends BaseTest {

    @Test
    public void testOriginalDdl() {
        String extStr = "{\"createSql4PhyTable\":\"\",\"gsi\":true,"
            + "\"orginalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        DDLExtInfo extInfo = DDLExtInfo.parseExtInfo(extStr);
        Assert.assertEquals("alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3",
            extInfo.getOrginalDdl());
        Assert.assertEquals("alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3",
            extInfo.getActualOriginalSql());
        Assert.assertNull(extInfo.getOriginalDdl());
        Assert.assertTrue(extInfo.isOldVersionOriginalSql());

        extStr = "{\"createSql4PhyTable\":\"\",\"gsi\":true,"
            + "\"originalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        extInfo = DDLExtInfo.parseExtInfo(extStr);
        Assert.assertEquals("alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3",
            extInfo.getOriginalDdl());
        Assert.assertEquals("alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3",
            extInfo.getActualOriginalSql());
        Assert.assertNull(extInfo.getOrginalDdl());
        Assert.assertFalse(extInfo.isOldVersionOriginalSql());
    }

    @Test
    public void testGsi() {
        // fastjson 对gsi 和 isGsi是完全兼容的
        String extStr = "{\"createSql4PhyTable\":\"\",\"gsi\":true,"
            + "\"orginalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        DDLExtInfo extInfo = DDLExtInfo.parseExtInfo(extStr);
        Assert.assertTrue(extInfo.isGsi());

        extStr = "{\"createSql4PhyTable\":\"\",\"isGsi\":true,"
            + "\"originalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        extInfo = DDLExtInfo.parseExtInfo(extStr);
        Assert.assertTrue(extInfo.isGsi());

        // gson 对gsi 和 isGsi是不兼容的
        Gson gson = new Gson();
        extStr = "{\"createSql4PhyTable\":\"\",\"gsi\":true,"
            + "\"orginalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        extInfo = gson.fromJson(extStr, DDLExtInfo.class);
        Assert.assertFalse(extInfo.isGsi());

        extStr = "{\"createSql4PhyTable\":\"\",\"isGsi\":true,"
            + "\"originalDdl\":\"alter table `jiyuetest2` add global index g_i_1(a,b,c) partition by key(a) partitions 3\",\"taskId\":1625938869717319684}";
        extInfo = gson.fromJson(extStr, DDLExtInfo.class);
        Assert.assertTrue(extInfo.isGsi());
    }
}
