/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.service.StorageInfoService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ROLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_CHECK_DN_LEADER_BY_SHOW_STORAGE;

/**
 * 本测试需要连接metaDB，不具备可重复性
 * </p>
 * 由于binlog_storage_info表中含有名字为user的列，使用H2会报错，因此忽略该用例
 *
 * @author yudong
 * @since 2023/6/7 17:29
 **/
public class DnHostTest extends BaseTest {

    @SneakyThrows
    @Before
    public void before() {
        String sql = "INSERT INTO `storage_info` VALUES "
            + "(1,'2023-08-21 06:28:11','2023-08-21 06:28:11','test-cluster','test-cluster-rdkw-gms','test-cluster-rdkw-gms','test-cluster-rdkw-gms-0',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,1,''),"
            + "(2,'2023-08-21 06:28:11','2023-08-21 06:28:11','test-cluster','test-cluster-rdkw-dn-0','test-cluster-rdkw-dn-0','test-cluster-rdkw-dn-0',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,1,''),"
            + "(3,'2023-08-21 06:28:11','2023-08-21 06:28:11','test-cluster','test-cluster-rdkw-dn-1','test-cluster-rdkw-dn-1','test-cluster-rdkw-dn-1',3306,31306,'test_user','BBBBBBBBBBBBBBBBBBBBBB==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,1,''),"
            + "(4,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkw-gms','test-cluster-rdkw-gms','192.0.2.10',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(5,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkw-gms-1','test-cluster-rdkw-gms','192.0.2.11',3306,-1,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(6,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkw-gms-2','test-cluster-rdkw-gms','192.0.2.12',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,2,0,NULL,NULL,NULL,10000,4,2147483647,0,''),"
            + "(7,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkj-dn-0','test-cluster-rdkw-dn-0','192.0.2.10',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(8,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkj-dn-1','test-cluster-rdkw-dn-0','192.0.2.11',3306,-1,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(9,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkj-dn-2','test-cluster-rdkw-dn-0','192.0.2.12',3306,31306,'test_user','AAAAAAAAAAAAAAAAAAAAAA==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(10,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkq-dn-1','test-cluster-rdkw-dn-1','192.0.2.13',3306,31306,'test_user','BBBBBBBBBBBBBBBBBBBBBB==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(11,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkq-dn-2','test-cluster-rdkw-dn-1','192.0.2.14',3306,31306,'test_user','BBBBBBBBBBBBBBBBBBBBBB==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(12,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster','test-cluster-rdkq-dn-3','test-cluster-rdkw-dn-1','192.0.2.13',3306,31306,'test_user','BBBBBBBBBBBBBBBBBBBBBB==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,''),"
            + "(13,'2023-08-21 06:29:09','2023-08-21 06:29:09','test-cluster-readonly','test-cluster-rdkw-readonly-dn-0','test-cluster-rdkw-dn-0','192.0.2.15',3306,-1,'test_user','BBBBBBBBBBBBBBBBBBBBBB==',5,0,0,NULL,NULL,NULL,65535,4,2147483647,0,'');";

        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaJdbcTemplate.execute(sql);
    }

    @After
    public void after() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaJdbcTemplate.execute("truncate table storage_info");
    }

    @Test
    public void getNormalDnHostTest() {
        prepareStorageInfoService();

        DnHost normalDnHost = DnHost.getNormalDnHost("test-cluster-rdkw-dn-0");
        Assert.assertEquals("192.0.2.10", normalDnHost.getIp());
        Assert.assertEquals(3306, normalDnHost.getPort().intValue());

        mockConfig(TOPOLOGY_CHECK_DN_LEADER_BY_SHOW_STORAGE, "true");
        normalDnHost = DnHost.getNormalDnHost("test-cluster-rdkw-dn-0");
        Assert.assertEquals("192.0.2.11", normalDnHost.getIp());
        Assert.assertEquals(3306, normalDnHost.getPort().intValue());

        try {
            StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
            service.setMasterUrlProvider(i -> null);
            service.setDnLeaderChecker(i -> false);
            DnHost.getNormalDnHost("test-cluster-rdkw-dn-0");
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals("cannot get storage info from metaDB!", e.getMessage());
        }
    }

    @Test
    public void getLocalDnHostTest() {
        prepareStorageInfoService();

        mockConfig(POLARX_INST_ID, "test-cluster-readonly");
        DnHost localDnHost = DnHost.getLocalDnHost("test-cluster-rdkw-dn-0");
        Assert.assertEquals("192.0.2.15", localDnHost.getIp());
        Assert.assertEquals(3306, localDnHost.getPort().intValue());

        mockConfig(POLARX_INST_ID, "test-cluster-tmp");
        try {
            DnHost.getLocalDnHost("test-cluster-rdkw-dn-0");
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals("cannot get storage info from metaDB!", e.getMessage());
        }
    }

    @Test
    public void testBuildHostForExtractorTest_4_Master() {
        prepareStorageInfoService();
        mockConfig(POLARX_INST_ID, "test-cluster");

        List<DnHost> resultList = DnHost.buildHostForExtractor("test-cluster-rdkw-dn-0");
        Assert.assertEquals(3, resultList.size());
        Assert.assertEquals("test-cluster-rdkj-dn-0", resultList.get(0).getStorageInstId());
        Assert.assertEquals("test-cluster-rdkj-dn-1", resultList.get(1).getStorageInstId());
        Assert.assertEquals("test-cluster-rdkj-dn-2", resultList.get(2).getStorageInstId());
    }

    @Test
    public void testBuildHostForExtractorTest_4_Slave() {
        prepareStorageInfoService();
        mockConfig(POLARX_INST_ID, "test-cluster-readonly");
        mockConfig(CLUSTER_ROLE, "slave");

        List<DnHost> dnHostList = DnHost.buildHostForExtractor("test-cluster-rdkw-dn-0");
        Assert.assertEquals(1, dnHostList.size());
        Assert.assertEquals("test-cluster-rdkw-readonly-dn-0", dnHostList.get(0).getStorageInstId());
        Assert.assertEquals("192.0.2.15", dnHostList.get(0).getIp());
        Assert.assertEquals(3306, dnHostList.get(0).getPort().intValue());

        mockConfig(TASK_DUMP_SAME_REGION_STORAGE_BINLOG, "false");
        dnHostList = DnHost.buildHostForExtractor("test-cluster-rdkw-dn-0");
        Assert.assertEquals(3, dnHostList.size());
        Assert.assertEquals("test-cluster-rdkj-dn-0", dnHostList.get(0).getStorageInstId());
        Assert.assertEquals("test-cluster-rdkj-dn-1", dnHostList.get(1).getStorageInstId());
        Assert.assertEquals("test-cluster-rdkj-dn-2", dnHostList.get(2).getStorageInstId());

    }

    private void prepareStorageInfoService() {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        service.setMasterUrlProvider(i -> "192.0.2.11:3306");
        service.setDnHealthChecker(i -> true);
        service.setDnLeaderChecker(i -> i.getIp().equals("192.0.2.10") && i.getPort().equals(3306));
        service.setFollowerStorageByClusterLocal(
            list -> {
                list.removeIf(
                    i -> i.getStorageInstId().equals("test-cluster-rdkj-dn-0") ||
                        i.getStorageInstId().equals("test-cluster-rdkw-dn-0"));
                return list;
            });
    }
}
