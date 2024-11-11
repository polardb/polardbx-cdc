/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.storage.RocksDBOptionUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * created by ziyang.lb
 **/
public class RocksDBOptionUtilTest {

    @Test
    public void testGetSectionProperties() throws IOException {
        String path = System.getProperty("user.dir");
        path = StringUtils.substringBeforeLast(path, "/") + "/polardbx-cdc-common/src/main/resources/rocksdb.ini";

        File iniFile = new File(path);
        Properties properties1 = RocksDBOptionUtil.getSectionProperties(iniFile, "DBOptions");
        Assert.assertEquals(properties1.size(), 76);
        Properties properties2 = RocksDBOptionUtil.getSectionProperties(iniFile, "CFOptions");
        Assert.assertEquals(properties2.size(), 58);
    }
}
