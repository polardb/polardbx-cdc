/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final ConcurrentHashMap<Long, RplDdl> runningDbDdlTasks;
    private final boolean isDdlMasterRole;
    @Setter
    @Getter
    private volatile DbMetaCache dbMetaCache;

    private AsyncDdlMonitor() {
        this.executorService = Executors.newFixedThreadPool(1);
        this.running = new AtomicBoolean(false);
        this.runningAsyncDdlTasks = new ConcurrentHashMap<>();
        this.runningDbDdlTasks = new ConcurrentHashMap<>();
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

    public void submitDbDdl(RplDdl rplDdl) {
        runningDbDdlTasks.put(rplDdl.getId(), rplDdl);
    }

    public void removeDbDdl(RplDdl rplDdl) {
        runningDbDdlTasks.remove(rplDdl.getId());
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
            stopDbTasks();
            runningAsyncDdlTasks.clear();
            if (this.executorService != null) {
                this.executorService.shutdownNow();
            }

            log.info("async ddl monitor stopped, master: {}", isDdlMasterRole);
        }
    }

    void stopDbTasks() {
        for (RplDdl rplDdl : runningDbDdlTasks.values()) {
            try {
                killConnectionByToken(rplDdl.getToken());
            } catch (Throwable t) {
                log.error("process db ddl failed !!", t);
            }
        }
    }

    public void killConnectionByToken(String token) throws SQLException {
        DataSource defaultDataSource = dbMetaCache.getBuiltInDefaultDataSource();
        try (Connection conn = defaultDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "show full processlist where info like '%" + token
                    + "%' and info not like 'show full processlist%'")) {
            while (rs.next()) {
                int id = rs.getInt("Id");
                String info = rs.getString("Info");
                // Kill the connection
                stmt.execute("KILL " + id);
                System.out.printf("Killed connection with ID:" + id + " INFO:" + info);
            }
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
