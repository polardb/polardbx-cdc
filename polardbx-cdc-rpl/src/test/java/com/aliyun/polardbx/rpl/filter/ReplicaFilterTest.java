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
package com.aliyun.polardbx.rpl.filter;


import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.TestBase;

/**
 * @author shicai.xsc 2021/3/3 16:58
 * @since 5.0.0.0
 */
public class ReplicaFilterTest extends TestBase {

    @Before
    public void before() throws Exception { }

    @After
    public void after() throws Exception { }

    @Test
    public void test_Init_Succeed() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl");
        replicaMeta.setIgnoreDb("full_src_1, rpl");
        replicaMeta.setDoTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setIgnoreTable("full_src_1.t2, full_src_1.t3");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("(full_src_1, full_dst_1), (full_src_2, full_dst_2)");
        printChangeFilterSql(replicaMeta);
        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());
    }

    @Test
    public void no_Filter_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);
        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    //////////////// 以下测试用例为根据代码逻辑构造的单元测试，并对比了 Mysql 的行为 //////////////

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。
     */
    @Test
    public void hit_DoDb_Hit_Rewrite_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, full_src_2, full_dst_2");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("(full_src_1, full_dst_1), (full_src_2, full_dst_2)");
        printChangeFilterSql(replicaMeta);
        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));

        rowChange.setSchema("full_dst_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));

        rowChange.setSchema("full_src_2");
        rowChange.setTable("t1");
        Assert.assertFalse(filter.ignoreEvent(rowChange));

        rowChange.setSchema("full_dst_2");
        rowChange.setTable("t1");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。
     */
    @Test
    public void hit_DoTable_Hit_Rewrite_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl, full_dst_2");
        replicaMeta.setIgnoreDb("full_src_1, gbktest");
        replicaMeta.setDoTable("full_src_1.t1, full_src_1.t2, full_dst_1.t1, full_dst_2.t1");
        replicaMeta.setIgnoreTable("full_src_1.t2, full_src_1.t3");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("(full_src_1, full_dst_1), (full_src_2, full_dst_2)");
        printChangeFilterSql(replicaMeta);
        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));

        rowChange.setSchema("full_src_2");
        rowChange.setTable("t1");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。
     */
    @Test
    public void hit_DoTable_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl");
        replicaMeta.setIgnoreDb("full_src_1, rpl");
        replicaMeta.setDoTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);
        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Hit_IgnoreTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoWildTable_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Miss_DoWildTable_Hit_IgnoreWildTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoDb_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, dutf8");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Miss_DoDb_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, full_src_2");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_IgnoreDb_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Miss_IgnoreDb_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, full_src_2");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoWildTable_Hit_DoDb_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, dutf8");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoWildTable_Miss_DoDb_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, full_src_2");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 false。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoWildTable_Hit_IgnoreDb_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。tbOk 返回 true。dbOk 返回 true。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Hit_DoWildTable_Miss_IgnoreDb_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, full_src_2");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    //////////////// 以下测试用例为单元测试外的补充测试用例，并对比了 Mysql 的行为 //////////////

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_Hit_DoDb_Hit_WildDoTable_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl, dutf8");
        replicaMeta.setIgnoreDb("full_src_1, rpl, dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_No_DoDb_Miss_IgnoreTable_Miss_IgnoreDb_Hit_WildDoTable_Keep() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, rpl");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertFalse(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void miss_DoTable_Miss_IgnoreTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl");
        replicaMeta.setIgnoreDb("full_src_1, rpl");
        replicaMeta.setDoTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        // Hit Replicate_Do_Table
        rowChange.setSchema("full_src_1");
        rowChange.setTable("t3");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_Hit_DoDb_Miss_WildDoTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl, dutf8");
        replicaMeta.setIgnoreDb("full_src_1, rpl, dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_Miss_DoDb_Hit_WildDoTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("full_src_1, rpl");
        replicaMeta.setIgnoreDb("full_src_1, rpl");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_No_DoDb_Hit_IgnoreDb_Hit_WildDoTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("full_src_1, dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t1, full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        // Hit Replicate_Do_Table
        rowChange.setSchema("dutf8");
        rowChange.setTable("tb_charset_gbk");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }

    /**
     * 与 Mysql 行为一致。
     */
    @Test
    public void no_DoTable_No_DoDb_Miss_IgnoreTable_Miss_IgnoreDb_Miss_WildDoTable_Ignore() {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("dutf8");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("full_src_1.t2");
        replicaMeta.setWildDoTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setWildIgnoreTable("d%.tb\\_charset%, d%.col\\_charset%");
        replicaMeta.setRewriteDb("");
        printChangeFilterSql(replicaMeta);

        // init
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        Assert.assertTrue(filter.init());

        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.INSERT);

        // Hit Replicate_Do_Table
        rowChange.setSchema("full_src_1");
        rowChange.setTable("t1");
        Assert.assertTrue(filter.ignoreEvent(rowChange));
    }
}
