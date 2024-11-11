/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.pipeline;

import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.util.Shell;
import com.aliyun.polardbx.binlog.util.Shell.ExitCodeException;
import com.aliyun.polardbx.binlog.util.Shell.ShellCommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;

/**
 * Created by ShuGuang
 */
@Slf4j
public class CommandPipeline {

    private String home;
    private File homeDir;

    public CommandPipeline() {
        this(System.getProperty(Shell.SYSPROP_CDC_HOME_DIR));
    }

    public CommandPipeline(String home) {
        if (StringUtils.isEmpty(home)) {
            home = "/tmp";
        }
        System.setProperty(Shell.SYSPROP_CDC_HOME_DIR, home);
        this.home = home;
        this.homeDir = new File(home);
    }

    public void startTask(String taskName) throws Exception {
        startTask(taskName, 1024);
    }

    /**
     * @param mem 启动内存，单位Mb
     */
    public void startTask(String taskName, int mem) throws Exception {
        execCommand(new String[] {"bash", "-c", "sh bin/start.sh " + taskName + " " + mem}, 1000);
    }

    public void stopTask(String taskName) throws Exception {
        execCommand(new String[] {"bash", "-c", "sh bin/stop.sh " + taskName}, 1000);
    }

    /**
     * @param mem 启动内存，单位Mb
     */
    public void restartTask(String taskName, int mem) throws Exception {
        stopTask(taskName);
        startTask(taskName);
    }

    public void startRplTask(long taskId, String taskName, int memory) throws Exception {
        log.info("Starting rpl task. Task id: {}, name: {}", taskId, taskName);
        execCommand(new String[] {
            "bash", "-c", "sh bin/start_rpl.sh " + taskId + " " + taskName + " "
            + memory}, 1000);
    }

    public void stopRplTask(long taskId) throws Exception {
        log.info("Stopping rpl task. Task id: {}", taskId);
        execCommand(new String[] {"bash", "-c", "sh bin/stop_rpl.sh " + taskId}, 1000);
    }

    public CommandResult execCommand(String[] commands, long timeout) throws Exception {
        CommandResult result = new CommandResult();
        ShellCommandExecutor shexc = new ShellCommandExecutor(commands,
            homeDir, null, timeout);
        try {
            shexc.execute();
            result.setCode(shexc.getExitCode());
            result.setMsg(shexc.getOutput());
        } catch (ExitCodeException e) {
            log.error("execCommand fail [{}]", Arrays.toString(commands), e);
            result.setCode(e.getExitCode());
            result.setMsg(e.getMessage());
        }
        log.debug("command result [{}] {}", Arrays.toString(commands), result);
        return result;
    }

}
