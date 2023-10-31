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
package com.aliyun.polardbx.rpl.dbmeta;

import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;

/**
 * @author shicai.xsc 2021/3/25 16:48
 * @since 5.0.0.0
 */
public class DbMetaManagerTest extends RplWithGmsTablesBaseTest {

    @Test
    public void metaTest() throws Throwable {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_task";
        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        Assert.assertNotNull(tableInfo);
    }
}
