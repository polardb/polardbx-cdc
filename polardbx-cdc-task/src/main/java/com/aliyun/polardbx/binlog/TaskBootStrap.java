/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.task.TaskHeartbeat;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getClusterType;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class TaskBootStrap {

    private TaskRuntimeConfigProvider taskRuntimeConfigProvider;

    public static void main(String[] args) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskRuntimeConfigProvider(new TaskRuntimeConfigProvider(handleArgs(args[0]).get(TASK_NAME)));
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
            log.info("## prepare to start task!");

            Map<String, String> argsMap = handleArgs(args[0]);
            String taskName = argsMap.get(TASK_NAME);
            System.setProperty(TASK_NAME, taskName);

            // spring context
            final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();

            // try process compatibility
            TableCompatibilityProcessor.process();
            TableCompatibilityProcessorWithTask.process();

            // construction
            final TaskRuntimeConfig taskRuntimeConfig = taskRuntimeConfigProvider.getTaskRuntimeConfig();
            final TaskController controller = new TaskController(getString(CLUSTER_ID),
                taskRuntimeConfig, taskRuntimeConfigProvider);
            final TaskHeartbeat taskHeartbeat = buildTaskHeartbeat(controller, taskRuntimeConfig, taskName);
            LabEventManager.logEvent(LabEventType.FINAL_TASK_START);

            // do start
            log.info("## starting the task, with name {}, with version {}:{}.",
                taskName, taskRuntimeConfig.getExecutionConfig().getRuntimeVersion(),
                taskRuntimeConfig.getExecutionConfig().getSubRuntimeVersion());
            taskHeartbeat.start();
            controller.start();

            log.info("## the task is running now ......");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("## stop the task");
                    LabEventManager.logEvent(LabEventType.FINAL_TASK_STOP);
                    taskHeartbeat.stop();
                    controller.stop();
                    appContextBootStrap.close();
                } catch (Throwable e) {
                    log.warn("##something goes wrong when stopping the task", e);
                } finally {
                    log.info("## task is down.");
                }
            }));
        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the task process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public void setTaskRuntimeConfigProvider(TaskRuntimeConfigProvider taskRuntimeConfigProvider) {
        this.taskRuntimeConfigProvider = taskRuntimeConfigProvider;
    }

    private TaskHeartbeat buildTaskHeartbeat(TaskController taskController, TaskRuntimeConfig taskRuntimeConfig,
                                             String taskName) {
        return new TaskHeartbeat(getString(CLUSTER_ID),
            getClusterType(),
            taskName,
            getInt(TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS),
            taskRuntimeConfig,
            taskRuntimeConfigProvider,
            new TaskSubVersionChangeCallback(taskController, i -> Runtime.getRuntime().halt(1)));
    }
}
