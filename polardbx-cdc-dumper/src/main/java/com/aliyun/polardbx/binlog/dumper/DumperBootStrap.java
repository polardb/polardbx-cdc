/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DumperConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.TableCompatibilityProcessor;
import com.aliyun.polardbx.binlog.TaskRuntimeConfigProvider;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.task.TaskHeartbeat;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getClusterType;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class DumperBootStrap {
    private TaskRuntimeConfigProvider taskRuntimeConfigProvider;

    public static void main(String[] args) {
        DumperBootStrap bootStrap = new DumperBootStrap();
        bootStrap.setTaskRuntimeConfigProvider(new TaskRuntimeConfigProvider(handleArgs(args[0]).get(TASK_NAME)));
        bootStrap.boot(args);
    }

    public static Map<String, String> handleArgs(String arg) {
        Map<String, String> propMap = new HashMap<>();
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
            log.info("## prepare to start dumper!");
            // spring context
            final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();

            // try process compatibility
            TableCompatibilityProcessor.process();

            // initial DumpConfig
            Map<String, String> argsMap = Maps.newHashMap();
            if (args.length > 0) {
                argsMap = handleArgs(args[0]);
            }
            String taskName = argsMap.get(TASK_NAME);
            System.setProperty(TASK_NAME, taskName);

            // you can only increment this version value
            DynamicApplicationConfig.setValue(DumperConfigKeys.DUMPER_VERSION, String.valueOf(1));

            // construction
            final TaskRuntimeConfig taskRuntimeConfig = taskRuntimeConfigProvider.getTaskRuntimeConfig();
            final DumperController controller = new DumperController(taskRuntimeConfig);
            final TaskHeartbeat taskHeartbeat = buildTaskHeartbeat(controller, taskName, taskRuntimeConfig);

            // do start
            log.info("## starting the dumper, with name {}, with version {}:{}.",
                taskName, taskRuntimeConfig.getExecutionConfig().getRuntimeVersion(),
                taskRuntimeConfig.getExecutionConfig().getSubRuntimeVersion());
            taskHeartbeat.start();
            controller.start();

            log.info("## the dumper is running now ......");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("## stop the dumper.");
                    taskHeartbeat.stop();
                    controller.stop();
                    appContextBootStrap.close();
                } catch (Throwable e) {
                    log.warn("##something goes wrong when stopping the dumper", e);
                } finally {
                    log.info("## dumper is down.");
                }
            }));
        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the dumper process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public void setTaskRuntimeConfigProvider(TaskRuntimeConfigProvider taskRuntimeConfigProvider) {
        this.taskRuntimeConfigProvider = taskRuntimeConfigProvider;
    }

    private TaskHeartbeat buildTaskHeartbeat(DumperController controller, String taskName,
                                             TaskRuntimeConfig taskRuntimeConfig) {

        DumperSubVersionChangeCallback subVersionChangeCallback = new DumperSubVersionChangeCallback(controller);
        TaskHeartbeat taskHeartbeat = new TaskHeartbeat(getString(ConfigKeys.CLUSTER_ID),
            getClusterType(),
            taskName,
            getInt(TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS),
            taskRuntimeConfig,
            taskRuntimeConfigProvider,
            subVersionChangeCallback);
        taskHeartbeat.setDumperStatisticSupplier(() -> controller.getLogFileManagerCollection().getCursorProviders());
        return taskHeartbeat;
    }
}
