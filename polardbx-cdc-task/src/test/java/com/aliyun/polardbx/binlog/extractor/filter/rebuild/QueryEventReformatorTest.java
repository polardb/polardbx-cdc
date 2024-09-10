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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.QueryEventReformator;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class QueryEventReformatorTest extends BaseTestWithGmsTables {

    @Test
    public void ignoreTruncateTableTest() {
        QueryEventReformator reformator = Mockito.mock(QueryEventReformator.class);
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("truncate table test");
        Mockito.when(queryLogEvent.getDbName()).thenReturn("test_db");
        Mockito.when(reformator.accept(queryLogEvent)).thenCallRealMethod();
        Assert.assertFalse(reformator.accept(queryLogEvent));
    }

    @Test
    public void ignoreTruncateTableExceptionTest() {
        QueryEventReformator reformator = Mockito.mock(QueryEventReformator.class);
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("this is not a ddl st");
        Mockito.when(queryLogEvent.getDbName()).thenReturn("test_db");
        Mockito.when(reformator.accept(queryLogEvent)).thenCallRealMethod();
        setConfig(ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_FILTER_IGNORE_PARSE_ERROR, "false");
        Exception ex = null;
        try {
            reformator.accept(queryLogEvent);
        } catch (Exception e) {
            ex = e;
        }
        Assert.assertNotNull(ex);
        setConfig(ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_FILTER_IGNORE_PARSE_ERROR, "true");
        Assert.assertTrue(reformator.accept(queryLogEvent));
    }
}
