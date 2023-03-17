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
package com.aliyun.polardbx.binlog.dumper.plugin;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.plugin.IDumperPlugin;
import com.aliyun.polardbx.binlog.plugin.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理所有的Dumper相关插件
 */
public class DumperPluginManager {

    private static final Logger logger = LoggerFactory.getLogger(DumperPluginManager.class);
    private final Set<IDumperPlugin> pluginSet = new HashSet<>();
    private final PluginContext context;

    public DumperPluginManager() {
        this.context = new PluginContext();
    }

    public void load(String taskName, TaskType taskType, String groupName, List<String> streamList,
                     long runtimeVersion) {
        logger.info("begin to load dumper plugin");
        Map<String, IDumperPlugin> beanMap = SpringContextHolder.getBeansOfType(IDumperPlugin.class);
        context.setBinlogFileRootPath(BinlogFileUtil.getBinlogFileRootPath(taskType, runtimeVersion));
        context.setTaskName(taskName);
        context.setTaskType(taskType);
        context.setRuntimeVersion(runtimeVersion);
        context.setGroup(groupName);
        context.setStreamList(streamList);
        try {
            for (Map.Entry<String, IDumperPlugin> bean : beanMap.entrySet()) {
                logger.info("load plugin : " + bean.getKey());
                IDumperPlugin plugin = bean.getValue();
                plugin.init(context);
                pluginSet.add(plugin);
            }
        } catch (Exception e) {
            throw new PolardbxException("load dumper plugins failed!", e);
        }
        logger.info("load dumper plugin finish!");
    }

    public void start() {
        logger.info("begin start plugin");
        for (IDumperPlugin plugin : pluginSet) {
            plugin.start(context);
        }
        logger.info("start plugin finish");
    }

    public void stop() {
        logger.info("begin stop plugin");
        for (IDumperPlugin plugin : pluginSet) {
            try {
                plugin.stop(context);
            } catch (Exception e) {
                logger.error("stop plugin failed!" + plugin, e);
            }
        }
        logger.info("stop plugin finish");
    }
}
