/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class ExecutionSnapshot {
    private boolean isOK;
    private Map<String, ProcessMeta> processMeta = new HashMap<>();

    public Set<String> downProcessContainers() {
        return processMeta.values().stream().filter(m -> m.getStatus() == Status.DOWN).map(ProcessMeta::getContainer)
            .collect(Collectors.toSet());
    }

    public boolean isOK() {
        return isOK;
    }

    public void setOK(boolean OK) {
        isOK = OK;
    }

    public Map<String, ProcessMeta> getProcessMeta() {
        return processMeta;
    }

    public void setProcessMeta(Map<String, ProcessMeta> processMeta) {
        this.processMeta = processMeta;
    }

    public static enum Status {
        OK, DOWN
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
