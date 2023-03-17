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
package com.aliyun.polardbx.rpl;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskBasedDiscriminator;
import com.aliyun.polardbx.rpl.storage.RplStorage;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * @author shicai.xsc 2020/11/27 11:28
 * @since 5.0.0.0
 */
@Slf4j
public class RplTaskEngine {

    public static void main(String[] args) {
        try {
            Map<String, String> argsMap = CommonUtil.handleArgs(args[0]);
            String taskId = argsMap.get(RplConstants.TASK_ID);
            String taskName = argsMap.get(RplConstants.TASK_NAME);
            TaskBasedDiscriminator.put(RplConstants.TASK_ID, String.valueOf(taskId));
            TaskBasedDiscriminator.put(RplConstants.TASK_NAME, taskName);
            System.setProperty(TASK_NAME, taskName);

            // spring context
            log.info("Spring context loading");
            SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();
            log.info("Spring context loaded");

            if (!RuntimeLeaderElector.isLeader("RplTaskEngine_" + taskId)) {
                log.error("another process is already running, exit");
                return;
            }

            MonitorManager.getInstance().startup();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("## stop rpl task.");
                    MonitorManager.getInstance().shutdown();
                } catch (Throwable e) {
                    log.warn("##something goes wrong when stopping rpl task.", e);
                } finally {
                    log.info("## rpl task is down.");
                }
            }));

            // init storage
            RplStorage.init();

            RplTask task = DbTaskMetaManager.getTask(Long.parseLong(taskId));
            if (task.getType().equals(ServiceType.REC_COMBINE.getValue())) {
                FlashbackResultCombiner taskRunner = new FlashbackResultCombiner(Long.parseLong(taskId));
                taskRunner.run();
            } else {
                RplTaskRunner taskRunner = new RplTaskRunner(Long.parseLong(taskId));
                taskRunner.start();
            }

            log.info("RplTaskEngine end");
        } catch (Throwable e) {
            log.error("RplTaskEngine exit by exception", e);
        }
    }
}
