/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@Getter
@AllArgsConstructor
@ToString
public class StreamEntity implements Comparable<StreamEntity> {
    private final String streamName;
    private final long timestamp;

    public StreamEntity(String streamName) {
        this.streamName = streamName;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        StreamEntity that = (StreamEntity) object;
        return Objects.equals(streamName, that.streamName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamName);
    }

    @Override
    public int compareTo(StreamEntity o) {
        return streamName.compareTo(o.streamName);
    }
}
