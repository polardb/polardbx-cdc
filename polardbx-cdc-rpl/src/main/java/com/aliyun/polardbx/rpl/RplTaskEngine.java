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

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskBasedDiscriminator;
import com.aliyun.polardbx.rpl.storage.RplStorage;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidRunner;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_REPLICA;
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
            System.setProperty(IS_REPLICA, CommonConstants.TRUE);

            // spring context
            log.info("Spring context loading");
            SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();
            log.info("Spring context loaded");

            if (!RuntimeLeaderElector.isLeader(RplConstants.RPL_TASK_LEADER_LOCK_PREFIX + taskId)) {
                log.error("find another process is already running in startup progress, will exit");
                throw new PolardbxException("duplicate replica task process error!!");
            }

            MonitorManager.getInstance().startup();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("## stop rpl task.");
                    MonitorManager.getInstance().shutdown();
                    appContextBootStrap.close();
                } catch (Throwable e) {
                    log.warn("##something goes wrong when stopping rpl task.", e);
                } finally {
                    log.info("## rpl task is down.");
                }
            }));

            // init storage
            RplStorage.init();

            RplTask task = DbTaskMetaManager.getTask(Long.parseLong(taskId));
            if (ServiceType.valueOf(task.getType()) == ServiceType.REC_COMBINE) {
                FlashbackResultCombiner taskRunner = new FlashbackResultCombiner(Long.parseLong(taskId));
                taskRunner.run();
            } else if (ServiceType.valueOf(task.getType()) == ServiceType.REPLICA_FULL_VALIDATION) {
                ReplicaFullValidRunner runner = ReplicaFullValidRunner.getInstance();
                runner.setFsmId(task.getStateMachineId());
                runner.setRplTaskId(task.getId());
                runner.run();
            } else {
                RplTaskRunner taskRunner = new RplTaskRunner(Long.parseLong(taskId));
                taskRunner.start();
            }

            log.info("RplTaskEngine end");
        } catch (Throwable e) {
            log.error("RplTaskEngine exit by exception", e);
            // 执行到这里，说明出现了一些非预期的情况，直接强制退出
            Runtime.getRuntime().halt(1);
        }
    }
}
