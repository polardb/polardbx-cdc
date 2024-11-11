/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

    @Test
    public void testVariables() {
        // fastjson 对gsi 和 isGsi是完全兼容的
        String extStr =
            "{\"cci\":false,\"createSql4PhyTable\":\"\",\"ddlScope\":0,\"enableImplicitTableGroup\":true,\"foreignKeysDdl\":false,\"gsi\":false,\"originalDdl\":\"\",\"polarxVariables\":{\"FP_OVERRIDE_NOW\":\"2024-09-20\"},\"sqlMode\":\"STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION\",\"taskId\":1762551824537092096}";
        DDLExtInfo extInfo = DDLExtInfo.parseExtInfo(extStr);
        Assert.assertFalse(extInfo.isGsi());

        Assert.assertFalse(extInfo.isCci());

        Assert.assertNotNull(extInfo.getPolarxVariables());

        Assert.assertEquals(1, extInfo.getPolarxVariables().size());

        Assert.assertEquals("2024-09-20", extInfo.getPolarxVariables().get("FP_OVERRIDE_NOW"));
    }

}
