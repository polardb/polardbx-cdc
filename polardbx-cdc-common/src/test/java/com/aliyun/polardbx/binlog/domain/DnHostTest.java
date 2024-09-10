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
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.service.StorageInfoService;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;

/**
 * 本测试需要连接metaDB，不具备可重复性
 * </p>
 * 由于binlog_storage_info表中含有名字为user的列，使用H2会报错，因此忽略该用例
 *
 * @author yudong
 * @since 2023/6/7 17:29
 **/
public class DnHostTest extends BaseTestWithGmsTables {

    @Test
    public void getNormalDnHostTest() {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        service.setMasterUrlProvider(i -> null);
        service.setDnHealthChecker(i -> true);

        DnHost normalDnHost = DnHost.getNormalDnHost("xrelease-230821142340-71dc-rdkw-dn-0");
        Assert.assertEquals(3306, normalDnHost.getPort().intValue());
    }

    @Test
    public void getLocalDnHostTest() {
        setConfig(POLARX_INST_ID, "xrelease-230821142340-71dc-readonly");

        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        service.setMasterUrlProvider(i -> null);
        service.setDnHealthChecker(i -> true);

        DnHost localDnHost = DnHost.getLocalDnHost("xrelease-230821142340-71dc-rdkw-dn-1");
        Assert.assertEquals("10.20.16.107", localDnHost.getIp());
    }

    @SneakyThrows
    @Before
    public void insertStorageInfo() {
        String sql = "INSERT INTO `storage_info` VALUES "
            + "(1,'2023-08-21 06:28:11','2023-08-21 06:28:11','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-gms','xrelease-230821142340-71dc-rdkw-gms','xrelease-230821142340-71dc-rdkw-gms-0',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,1,''),"
            + "(2,'2023-08-21 06:28:11','2023-08-21 06:28:11','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-dn-0','xrelease-230821142340-71dc-rdkw-dn-0','xrelease-230821142340-71dc-rdkw-dn-0',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,1,''),"
            + "(3,'2023-08-21 06:28:11','2023-08-21 06:28:11','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-dn-1','xrelease-230821142340-71dc-rdkw-dn-1','xrelease-230821142340-71dc-rdkw-dn-1',3306,31306,'admin','oAc9lBUa8XTBh7UIrAlswA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,1,''),"
            + "(4,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-gms','xrelease-230821142340-71dc-rdkw-gms','10.20.16.179',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(5,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-gms-1','xrelease-230821142340-71dc-rdkw-gms','10.20.2.198',3306,-1,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(6,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkw-gms-2','xrelease-230821142340-71dc-rdkw-gms','10.20.1.11',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(7,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkj-dn-0','xrelease-230821142340-71dc-rdkw-dn-0','10.20.16.179',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(8,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkj-dn-1','xrelease-230821142340-71dc-rdkw-dn-0','10.20.2.198',3306,-1,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(9,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkj-dn-2','xrelease-230821142340-71dc-rdkw-dn-0','10.20.1.11',3306,31306,'admin','FWTet1pPkPuSgZsGVGnP9A==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(10,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkq-dn-1','xrelease-230821142340-71dc-rdkw-dn-1','10.20.2.187',3306,31306,'admin','oAc9lBUa8XTBh7UIrAlswA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(11,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkq-dn-2','xrelease-230821142340-71dc-rdkw-dn-1','10.20.1.100',3306,31306,'admin','oAc9lBUa8XTBh7UIrAlswA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(12,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc','xrelease-230821142340-71dc-rdkq-dn-3','xrelease-230821142340-71dc-rdkw-dn-1','10.20.2.187',3306,31306,'admin','oAc9lBUa8XTBh7UIrAlswA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(13,'2023-08-21 06:29:09','2023-08-21 06:29:09','xrelease-230821142340-71dc-readonly','xrelease-230821142340-71dc-rdkw-readonly-dn-1','xrelease-230821142340-71dc-rdkw-dn-1','10.20.16.107',3306,-1,'admin','oAc9lBUa8XTBh7UIrAlswA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,'');";

        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaJdbcTemplate.execute(sql);
    }

    @After
    public void afterTruncateStorageInfo() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaJdbcTemplate.execute("truncate table storage_info");
    }
}
