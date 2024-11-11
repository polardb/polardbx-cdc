/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.monitor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import org.joda.time.DateTime;

/**
 * Created by ziyang.lb
 **/
public class MonitorMsgBuilder {

    public static String buildMessage(MonitorType monitorType, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("【报警类型】");
        sb.append(monitorType.getDesc());
        sb.append("\n");

        sb.append("【报警节点】");
        sb.append(DynamicApplicationConfig.getString(ConfigKeys.INST_IP)).append(" : ")
            .append(DynamicApplicationConfig.getString(ConfigKeys.TASK_NAME));
        sb.append("\n");

        sb.append("【报警时间】");
        sb.append(DateTime.now().toString("yyyy-MM-dd HH:mm:ss"));
        sb.append("\n");

        sb.append("【所属PolarX实例】");
        sb.append(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        sb.append("\n");

        sb.append("【所属ClusterID】");
        sb.append(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
        sb.append("\n");

        if (args.length > 0) {
            sb.append("【报警详情】");
            sb.append(String.format(monitorType.getMsgTemplate(), args));
            sb.append("\n");
        } else {
            sb.append("【报警详情】");
            sb.append(monitorType.getMsgTemplate());
            sb.append("\n");
        }

        return sb.toString();
    }
}
