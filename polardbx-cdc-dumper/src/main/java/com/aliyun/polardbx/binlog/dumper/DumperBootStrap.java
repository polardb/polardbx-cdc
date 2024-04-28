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
package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.TableCompatibilityProcessor;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
import com.aliyun.polardbx.binlog.task.TaskHeartbeat;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS;

/**
 * Created by ziyang.lb
 **/
public class DumperBootStrap {

    private static final Logger logger = LoggerFactory.getLogger(DumperBootStrap.class);
    private TaskConfigProvider taskConfigProvider;

    public static void main(String[] args) {
        DumperBootStrap bootStrap = new DumperBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider(handleArgs(args[0]).get(TASK_NAME)));
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
            logger.info("## prepare to start dumper!");
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

            // do start
            logger.info("## starting the dumper, with name {}.", taskName);
            final DumperController controller = new DumperController(taskConfigProvider);
            final TaskHeartbeat taskHeartbeat =
                new TaskHeartbeat(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID),
                    DynamicApplicationConfig.getClusterType(), taskName,
                    DynamicApplicationConfig.getInt(TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS),
                    taskConfigProvider.getTaskRuntimeConfig().getBinlogTaskConfig());
            taskHeartbeat.setCursorProviderMap(controller.getLogFileManagerCollection().getCursorProviders());
            taskHeartbeat.start();
            controller.start();

            logger.info("## the dumper is running now ......");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("## stop the dumper.");
                    taskHeartbeat.stop();
                    controller.stop();
                    appContextBootStrap.close();
                } catch (Throwable e) {
                    logger.warn("##something goes wrong when stopping the dumper", e);
                } finally {
                    logger.info("## dumper is down.");
                }
            }));
        } catch (Throwable t) {
            logger.error("## Something goes wrong when starting up the dumper process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public void setTaskConfigProvider(TaskConfigProvider taskConfigProvider) {
        this.taskConfigProvider = taskConfigProvider;
    }

}
