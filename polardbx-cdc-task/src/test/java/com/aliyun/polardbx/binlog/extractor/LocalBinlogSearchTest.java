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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV1;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

//import com.aliyun.polardbx.binlog.extractor.filter.DisrupterFilter;

public class LocalBinlogSearchTest {

    private static final Logger logger = LoggerFactory.getLogger(LocalBinlogSearchTest.class);

    @Before
    public void before() {
        SpringContextBootStrap bootStrap = new SpringContextBootStrap("spring/spring.xml");
        bootStrap.boot();
    }

    public void handleEvent() {

    }

    @Test
    public void searchPosition()
        throws Exception {
        long searchTso =
            Long.valueOf(CommonUtils.getTsoTimestamp("693079023943889721614618554549833646090000000000152429"));
        logger.info("search position by tso : " + searchTso);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();

        BinlogEventProcessor processor = new BinlogEventProcessor();
        SearchTsoEventHandleV1 searchTsoEventHandle =
            new SearchTsoEventHandleV1(authenticationInfo, null, searchTso, -1);
        searchTsoEventHandle.setTest(true);
        processor.setHandle(searchTsoEventHandle);
        String path = "/Users/yanfenglin/Downloads";
        String binlogFile = "mysql-bin.000111";
        ServerCharactorSet serverCharactorSet = new ServerCharactorSet("utf8", "utf8", "utf8", "utf8");
        ErosaConnection connection = new FakeConnection(path, null, null, null, null, 15, null, null);
        while (true) {
            searchTsoEventHandle.reset();
            processor.init(connection, binlogFile, 0, true, serverCharactorSet,
                null, 0, true);
            long binlogFileSize = connection.binlogFileSize(binlogFile);
            if (binlogFileSize == -1) {
                //找不到这个文件，直接break
                break;
            }
            logger.info("start search " + searchTso + " in " + binlogFile);
            searchTsoEventHandle.setCurrentFile(binlogFile);
            searchTsoEventHandle
                .setEndPosition(new BinlogPosition(binlogFile, binlogFileSize, -1, -1));
            processor.start();
            BinlogPosition startPosition = searchTsoEventHandle.searchResult();
            String topologyContext = searchTsoEventHandle.getTopologyContext();
            if (startPosition != null) {
                logger.error("find pos : " + startPosition);
            }
            break;
        }
        BinlogPosition startPosition = searchTsoEventHandle.getCommandPosition();
        if (startPosition != null) {
            String topologyContext = searchTsoEventHandle.getTopologyContext();
            logger.error("find pos : " + startPosition);
        }
        System.out.println("not find pos ");
    }

    @Test
    public void testSize() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("").openConnection();
        connection.connect();
        Map<String, List<String>> map = connection.getHeaderFields();
        List<String> properties = map.get("x-oss-next-append-position");
        if (CollectionUtils.isEmpty(properties)) {
            properties = map.get("Content-Length");
        }
        System.out.println(properties.get(0));
    }
}
