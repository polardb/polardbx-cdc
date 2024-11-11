/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
    private String ip; //该container可以启动的NodeManager的hostname
    private int daemonPort; //该container的daemon分配的port
    private LinkedList<Integer> availablePorts; //该container可用端口列表
    private String hostString;

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
        return mem == 0 ? this.ip.compareTo(o.ip) : mem;
    }

    @Override
    public String toString() {
        return "Container{" +
            "ip='" + ip + '\'' +
            '}';
    }
}