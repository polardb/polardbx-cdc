/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class BinlogExtractorTest extends BaseTest {
    @Test
    public void testInit() {
        try (MockedStatic<ServerConfigUtil> mockedStatic = Mockito.mockStatic(ServerConfigUtil.class)) {
            BinlogExtractor extractor = Mockito.mock(BinlogExtractor.class);
            Mockito.doCallRealMethod().when(extractor).init(any(), anyString(), anyLong(), any(), anyBoolean());
            Mockito.doCallRealMethod().when(extractor).initAuthenticationInfo();
            Mockito.doCallRealMethod().when(extractor).start(anyString());
            doNothing().when(extractor).startDnHealthChecker();

            List<Map<String, Object>> cdcTopology = new ArrayList<>();
            Map<String, Object> topologyMap = new HashMap<>();
            topologyMap.put("phy_db_name", "_cdc__001");
            cdcTopology.add(topologyMap);
            when(extractor.queryCdcTopology()).thenReturn(cdcTopology);

            List<DnHost> dnHostList = new ArrayList<>();
            dnHostList.add(
                new DnHost("127.0.0.1", 3306, "root", "123456", "utf8", "xrelease-230821142340-71dc-rdkw-dn-0"));
            dnHostList.add(
                new DnHost("127.0.0.1", 3307, "root", "123456", "utf8", "xrelease-230821142340-71dc-rdkw-dn-1"));
            when(extractor.buildDnHost(anyString())).thenReturn(dnHostList);

            BinlogParameter parameter = new BinlogParameter();
            parameter.setStorageInstId("xrelease-230821142340-71dc-rdkw-dn-0");
            String rdsBinlogPath = "";
            long serverId = 1;

            extractor.init(parameter, rdsBinlogPath, serverId, null, false);
            extractor.start("");

            Mockito.verify(extractor, Mockito.times(1)).queryCdcTopology();
            Mockito.verify(extractor, Mockito.times(1)).buildDnHost(anyString());
            Mockito.verify(extractor, Mockito.times(1)).initAuthenticationInfo();
        }
    }
}
