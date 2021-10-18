/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
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
