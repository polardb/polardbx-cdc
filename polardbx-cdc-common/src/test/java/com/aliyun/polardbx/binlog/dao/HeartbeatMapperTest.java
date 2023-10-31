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
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author yudong
 * @since 2023/7/3 11:56
 **/
public class HeartbeatMapperTest extends BaseTestWithGmsTables {

    @Test
    public void testGetAliveNodes() {
        BinlogNodeInfoMapper binlogNodeInfoMapper = SpringContextHolder.getObject(BinlogNodeInfoMapper.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

        NodeInfo node1 = new NodeInfo();
        node1.setClusterId("heartbeat-mapper-test");
        node1.setContainerId("1");
        node1.setStatus(0);
        node1.setIp("127.1");
        node1.setDaemonPort(1111);
        node1.setAvailablePorts("1111");

        NodeInfo node2 = new NodeInfo();
        node2.setClusterId("heartbeat-mapper-test");
        node2.setContainerId("2");
        node2.setStatus(0);
        node2.setIp("127.1");
        node2.setDaemonPort(2222);
        node2.setAvailablePorts("2222");

        nodeInfoMapper.delete(s -> s.where());

        nodeInfoMapper.insertSelective(node1);
        nodeInfoMapper.insertSelective(node2);
        List<NodeInfo> aliveNodes = binlogNodeInfoMapper.getAliveNodes("heartbeat-mapper-test", 5000);
        Assert.assertEquals(2, aliveNodes.size());

        try {
            Thread.sleep(5000);
        } catch (Exception e) {

        }

        aliveNodes = binlogNodeInfoMapper.getAliveNodes("heartbeat-mapper-test", 5000);
        Assert.assertEquals(0, aliveNodes.size());
    }

    @Test
    public void testGetDeadNodes() {
        BinlogNodeInfoMapper binlogNodeInfoMapper = SpringContextHolder.getObject(BinlogNodeInfoMapper.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

        NodeInfo node1 = new NodeInfo();
        node1.setClusterId("heartbeat-mapper-test");
        node1.setContainerId("1");
        node1.setStatus(0);
        node1.setIp("127.1");
        node1.setDaemonPort(1111);
        node1.setAvailablePorts("1111");

        NodeInfo node2 = new NodeInfo();
        node2.setClusterId("heartbeat-mapper-test");
        node2.setContainerId("2");
        node2.setStatus(0);
        node2.setIp("127.1");
        node2.setDaemonPort(2222);
        node2.setAvailablePorts("2222");

        nodeInfoMapper.delete(s -> s.where());

        nodeInfoMapper.insertSelective(node1);
        nodeInfoMapper.insertSelective(node2);
        List<NodeInfo> aliveNodes = binlogNodeInfoMapper.getDeadNodes("heartbeat-mapper-test", 5000);
        Assert.assertEquals(0, aliveNodes.size());

        try {
            Thread.sleep(5000);
        } catch (Exception e) {

        }

        aliveNodes = binlogNodeInfoMapper.getDeadNodes("heartbeat-mapper-test", 5000);
        Assert.assertEquals(2, aliveNodes.size());
    }
}
