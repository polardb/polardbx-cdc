/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
