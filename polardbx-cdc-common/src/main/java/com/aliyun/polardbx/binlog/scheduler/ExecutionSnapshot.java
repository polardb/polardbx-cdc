/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class ExecutionSnapshot {
    private boolean isAllRunningOk;
    private final Map<String, ProcessMeta> processMeta = new HashMap<>();

    public boolean isAllRunningOk() {
        return isAllRunningOk;
    }

    public void setAllRunningOk(boolean allRunningOk) {
        isAllRunningOk = allRunningOk;
    }

    public Map<String, ProcessMeta> getProcessMeta() {
        return processMeta;
    }

    public boolean isRunningOk4Container(String containerId) {
        return processMeta.values().stream()
            .filter(p -> StringUtils.equals(containerId, p.getContainer()) && p.getStatus() != Status.OK).collect(
                Collectors.toSet()).isEmpty();
    }

    public enum Status {
        /**
         * 运行正常
         */
        OK,
        /**
         * 运行异常
         */
        DOWN
    }

    public static class ProcessMeta {
        private Status status;
        private String container;
        private String desc;

        public ProcessMeta() {

        }

        public ProcessMeta(Status status, String container, String desc) {
            this.status = status;
            this.container = container;
            this.desc = desc;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getContainer() {
            return container;
        }

        public void setContainer(String container) {
            this.container = container;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }
}
