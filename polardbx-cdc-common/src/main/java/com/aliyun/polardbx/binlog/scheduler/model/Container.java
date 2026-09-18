/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Created by ShuGuang
 */
@Builder
@Data
public class Container {
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

    public static void sortByResourceDesc(List<Container> containerList) {
        containerList.sort(((o1, o2) -> -1 * compareResourceWithId(o1, o2)));
    }

    public static int compareResourceWithId(Container o1, Container o2) {
        int result = compareResource(o1, o2);
        return result == 0 ? o1.getContainerId().compareTo(o2.getContainerId()) : result;
    }

    public static int compareResource(Container o1, Container o2) {
        int free1 = o1.getCapability().getFreeMemMb();
        int free2 = o2.getCapability().getFreeMemMb();
        int result = Integer.compare(free1, free2);
        return result == 0 ? Integer.compare(o1.getCapability().getCpu(), o2.getCapability().getCpu()) : result;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        Container container = (Container) object;
        return Objects.equals(containerId, container.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerId);
    }

    @Override
    public String toString() {
        return "Container{" +
            "ip='" + ip + '\'' +
            '}';
    }
}