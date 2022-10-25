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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.task.TaskHeartbeat;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TYPE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_TASK_HEARTBEAT_INTERVAL;

/**
 * Created by ziyang.lb
 **/
public class TaskBootStrap {

    private static final Logger logger = LoggerFactory.getLogger(TaskBootStrap.class);
    private TaskInfoProvider taskInfoProvider;

    public static void main(String[] args) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskInfoProvider(new TaskInfoProvider(handleArgs(args[0]).get(TASK_NAME)));
        bootStrap.boot(args);
    }

    public static Map<String, String> handleArgs(String arg) {
        Map<String, String> propMap = new HashMap<String, String>();
        String[] argpiece = arg.split(" ");
        for (String argstr : argpiece) {
            String[] kv = argstr.split("=");
            if (kv.length == 2) {
                propMap.put(kv[0], kv[1]);
            } else if (kv.length == 1) {
                propMap.put(kv[0], StringUtils.EMPTY);
            } else {
                throw new RuntimeException("parameter format need to like: key1=value1 key2=value2 ...");
            }
        }
        return propMap;
    }

    public void boot(String[] args) {
        try {
            Map<String, String> argsMap = handleArgs(args[0]);
            String taskName = argsMap.get(TASK_NAME);
            System.setProperty(TASK_NAME, taskName);

            // spring context
            final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();

            // do start
            logger.info("## starting the task, with name {}.", taskName);
            final TaskController controller =
                new TaskController(DynamicApplicationConfig.getString(CLUSTER_ID), taskInfoProvider);
            controller.start();

            final TaskHeartbeat taskHeartbeat =
                new TaskHeartbeat(DynamicApplicationConfig.getString(CLUSTER_ID),
                    DynamicApplicationConfig.getString(CLUSTER_TYPE), taskName,
                    DynamicApplicationConfig.getInt(TOPOLOGY_TASK_HEARTBEAT_INTERVAL),
                    taskInfoProvider.get().getBinlogTaskConfig());
            taskHeartbeat.start();

            logger.info("## the task is running now ......");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("## stop the task");
                    taskHeartbeat.stop();
                    controller.stop();
                    appContextBootStrap.close();
                } catch (Throwable e) {
                    logger.warn("##something goes wrong when stopping the task", e);
                } finally {
                    logger.info("## task is down.");
                }
            }));
        } catch (Throwable t) {
            logger.error("## Something goes wrong when starting up the task process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public void setTaskInfoProvider(TaskInfoProvider taskInfoProvider) {
        this.taskInfoProvider = taskInfoProvider;
    }

}
