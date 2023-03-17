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
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;

import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_PASSWORD;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_USERNAME;

/**
 * @author shicai.xsc 2021/2/3 15:58
 * @since 5.0.0.0
 */
@Slf4j
public class HostManager {

    public static HostInfo getDstPolarxHost() {
        PolarxCNodeInfo node = getRandomNode();
        String user = DynamicApplicationConfig.getString(POLARX_USERNAME);
        String password = DynamicApplicationConfig.getString(POLARX_PASSWORD);
        return new HostInfo(node.getIp(), node.getPort(), user, password, "", HostType.POLARX2,
            RplConstants.SERVER_ID_NULL);
    }

    private static PolarxCNodeInfo getRandomNode() {
        List<PolarxCNodeInfo> nodes = DbTaskMetaManager.listPolarxCNodeInfo();
        int random = new Random().nextInt(nodes.size());
        return nodes.get(random);
    }
}
