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
package com.aliyun.polardbx.binlog.extractor.filter;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ziyang.lb
 **/
public class RebuildEventLogFilterTest {

    @Test
    public void testIsMoveDatabase() {
        String sql = "move database group1 to 'storage_xxx'";
        RebuildEventLogFilter filter = new RebuildEventLogFilter(0, null, null);
        boolean result = filter.isMoveDataBaseSql(sql);
        Assert.assertTrue(result);

        sql = "create table test(id int)";
        result = filter.isMoveDataBaseSql(sql);
        Assert.assertFalse(result);
    }

    @Test
    public void testTryRewriteSql() {
        RebuildEventLogFilter filter = new RebuildEventLogFilter(0, null, null);
        String s1 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb");
        String s2 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,xx.bb");
        String s3 = filter.tryRewriteDropTableSql("a`a", "b`b", "drop table `a``a`.`b``b`,`vv`.`b``b`,`a``a`.cc");
        String s4 = filter.tryRewriteDropTableSql("aa", "bb", "drop table bb,cc,dd");
        String s5 = filter.tryRewriteDropTableSql("aa", "bb", "drop table bb");
        String s6 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,aa.bb");
        System.out.println(s1);
        System.out.println(s2);
        System.out.println(s3);
        System.out.println(s4);
        System.out.println(s5);
        System.out.println(s6);
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s1));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s2));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table `a``a`.`b``b`", s3));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table bb", s4));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table bb", s5));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s6));
    }
}
