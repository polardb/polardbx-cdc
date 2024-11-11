/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.task.TaskHeartbeat;
import com.aliyun.polardbx.binlog.util.LabEventType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS;

/**
 * Created by ziyang.lb
 **/
public class TaskBootStrap {

    private static final Logger logger = LoggerFactory.getLogger(TaskBootStrap.class);
    private TaskConfigProvider taskConfigProvider;

    public static void main(String[] args) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider(handleArgs(args[0]).get(TASK_NAME)));
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

            // try process compatibility
            TableCompatibilityProcessor.process();
            TableCompatibilityProcessorWithTask.process();

            // do start
            logger.info("## starting the task, with name {}.", taskName);
            final TaskController controller =
                new TaskController(DynamicApplicationConfig.getString(CLUSTER_ID), taskConfigProvider);
            final TaskHeartbeat taskHeartbeat =
                new TaskHeartbeat(DynamicApplicationConfig.getString(CLUSTER_ID),
                    DynamicApplicationConfig.getClusterType(), taskName,
                    DynamicApplicationConfig.getInt(TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS),
                    taskConfigProvider.getTaskRuntimeConfig().getBinlogTaskConfig());
            LabEventManager.logEvent(LabEventType.FINAL_TASK_START);
            taskHeartbeat.start();
            controller.start();

            logger.info("## the task is running now ......");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("## stop the task");
                    LabEventManager.logEvent(LabEventType.FINAL_TASK_STOP);
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

    public void setTaskConfigProvider(TaskConfigProvider taskConfigProvider) {
        this.taskConfigProvider = taskConfigProvider;
    }

}
