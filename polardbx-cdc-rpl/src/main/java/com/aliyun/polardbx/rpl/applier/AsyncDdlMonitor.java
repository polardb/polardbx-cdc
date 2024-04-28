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
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.checkIfDdlSucceed;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.markDdlSucceed;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-11-02 14:21
 **/
@Slf4j
public class AsyncDdlMonitor {

    private static final AsyncDdlMonitor INSTANCE = new AsyncDdlMonitor();

    private final ExecutorService executorService;
    private final AtomicBoolean running;
    private final ConcurrentHashMap<Long, RplDdl> runningAsyncDdlTasks;
    private final boolean isDdlMasterRole;
    @Setter
    @Getter
    private volatile DbMetaCache dbMetaCache;

    private AsyncDdlMonitor() {
        this.executorService = Executors.newFixedThreadPool(1);
        this.running = new AtomicBoolean(false);
        this.runningAsyncDdlTasks = new ConcurrentHashMap<>();
        this.isDdlMasterRole = buildDdlMasterRole();
    }

    public static AsyncDdlMonitor getInstance() {
        return INSTANCE;
    }

    public void submitNewDdl(RplDdl rplDdl) {
        if (!isDdlMasterRole) {
            throw new PolardbxException("current task is not ddl master role, task id "
                + TaskContext.getInstance().getTaskId());
        }
        runningAsyncDdlTasks.put(rplDdl.getId(), rplDdl);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            if (isDdlMasterRole) {
                initRunningAsyncTasks();
                this.executorService.submit(() -> {
                    try {
                        while (running.get()) {
                            Iterator<Map.Entry<Long, RplDdl>> iterator = runningAsyncDdlTasks.entrySet().iterator();

                            while (iterator.hasNext()) {
                                Map.Entry<Long, RplDdl> item = iterator.next();
                                RplDdl rplDdl = item.getValue();

                                DataSource dataSource = StringUtils.isNotBlank(rplDdl.getSchemaName()) ? dbMetaCache
                                    .getDataSource(rplDdl.getSchemaName()) : dbMetaCache.getBuiltInDefaultDataSource();
                                boolean flag = checkIfDdlSucceed(dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
                                if (flag) {
                                    markDdlSucceed(rplDdl.getDdlTso(), true);
                                    iterator.remove();
                                    log.info("async ddl is success, {}", JSONObject.toJSONString(item.getValue()));
                                }
                            }

                            Thread.sleep(10000);
                        }
                    } catch (Throwable t) {
                        log.error("process async ddl failed !!", t);
                    }
                });
            }

            log.info("async ddl monitor started, master: {}", isDdlMasterRole);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            runningAsyncDdlTasks.clear();
            if (this.executorService != null) {
                this.executorService.shutdownNow();
            }

            log.info("async ddl monitor stopped, master: {}", isDdlMasterRole);
        }
    }

    private void initRunningAsyncTasks() {
        List<RplDdl> runningList = DbTaskMetaManager.getAsyncDdlTasksByState(
            TaskContext.getInstance().getStateMachineId(), DdlState.RUNNING);
        runningList.forEach(t -> runningAsyncDdlTasks.put(t.getId(), t));
        log.info("init running state async ddl tasks, {}.", runningAsyncDdlTasks.keys());
    }

    private boolean buildDdlMasterRole() {
        List<RplTask> rplTasks = DbTaskMetaManager.listTaskByService(TaskContext.getInstance().getServiceId());
        if (rplTasks.size() > 1) {
            long ddlMasterTaskId = rplTasks.stream().map(RplTask::getId).min(Long::compareTo).get();
            return ddlMasterTaskId == TaskContext.getInstance().getTaskId();
        } else {
            return true;
        }
    }
}
