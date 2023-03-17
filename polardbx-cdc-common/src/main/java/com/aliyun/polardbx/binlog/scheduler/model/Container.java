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
package com.aliyun.polardbx.binlog.scheduler.model;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedList;

/**
 * Created by ShuGuang
 */
@Builder
@Data
public class Container implements Comparable<Container> {
    private String containerId; //全局唯一的container标示
    private Resource capability;  //该container的资源信息
    private String nodeHttpAddress; //该container可以启动的NodeManager的hostname
    private int daemonPort; //该container的daemon分配的port
    private LinkedList<Integer> availablePorts; //该container可用端口列表

    /**
     * 扣减内存
     */
    public void deductMem(int mem) {
        this.getCapability().addUse(mem);
    }

    /**
     * 占用一个可用端口
     *
     * @return 可用端口
     */
    public int holdPort() {
        return availablePorts.pop();
    }

    @Override
    public int compareTo(Container o) {
        int mem = o.getCapability().getFreeMemMb() - this.getCapability().getFreeMemMb();
        return mem == 0 ? this.nodeHttpAddress.compareTo(o.nodeHttpAddress) : mem;
    }

    @Override
    public String toString() {
        return "Container{" +
            "ip='" + nodeHttpAddress + '\'' +
            '}';
    }
}