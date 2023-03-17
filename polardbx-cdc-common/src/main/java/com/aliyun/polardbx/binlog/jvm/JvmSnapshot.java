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
package com.aliyun.polardbx.binlog.jvm;

/**
 * Created by ziyang.lb on 2021/01/21.
 **/
public class JvmSnapshot {
    private long startTime;

    /**
     * 新生代已用内存
     */
    private long youngUsed;

    /**
     * 新生代最大内存
     */
    private long youngMax;

    /**
     * 老年代已用内存
     */
    private long oldUsed;

    /**
     * 老年代最大内存
     */
    private long oldMax;

    private long metaUsed;
    private long metaMax;

    /**
     * 新生代当前累积的垃圾回收次数
     */
    private long youngCollectionCount;

    /**
     * 老年代当前累积的垃圾回收次数
     */
    private long oldCollectionCount;

    /**
     * 新生代当前累积的垃圾回收时间
     */
    private long youngCollectionTime;

    /**
     * 老年代当前累积的垃圾回收时间
     */
    private long oldCollectionTime;

    /**
     * 当前线程数
     */
    private int currentThreadCount;

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getYoungUsed() {
        return youngUsed;
    }

    public void setYoungUsed(long youngUsed) {
        this.youngUsed = youngUsed;
    }

    public long getYoungMax() {
        return youngMax;
    }

    public void setYoungMax(long youngMax) {
        this.youngMax = youngMax;
    }

    public long getOldUsed() {
        return oldUsed;
    }

    public void setOldUsed(long oldUsed) {
        this.oldUsed = oldUsed;
    }

    public long getOldMax() {
        return oldMax;
    }

    public long getMetaUsed() {
        return metaUsed;
    }

    public void setMetaUsed(long metaUsed) {
        this.metaUsed = metaUsed;
    }

    public long getMetaMax() {
        return metaMax;
    }

    public void setMetaMax(long metaMax) {
        this.metaMax = metaMax;
    }

    public void setOldMax(long oldMax) {
        this.oldMax = oldMax;
    }

    public long getYoungCollectionCount() {
        return youngCollectionCount;
    }

    public void setYoungCollectionCount(long youngCollectionCount) {
        this.youngCollectionCount = youngCollectionCount;
    }

    public long getOldCollectionCount() {
        return oldCollectionCount;
    }

    public void setOldCollectionCount(long oldCollectionCount) {
        this.oldCollectionCount = oldCollectionCount;
    }

    public long getYoungCollectionTime() {
        return youngCollectionTime;
    }

    public void setYoungCollectionTime(long youngCollectionTime) {
        this.youngCollectionTime = youngCollectionTime;
    }

    public long getOldCollectionTime() {
        return oldCollectionTime;
    }

    public void setOldCollectionTime(long oldCollectionTime) {
        this.oldCollectionTime = oldCollectionTime;
    }

    public int getCurrentThreadCount() {
        return currentThreadCount;
    }

    public void setCurrentThreadCount(int currentThreadCount) {
        this.currentThreadCount = currentThreadCount;
    }
}
