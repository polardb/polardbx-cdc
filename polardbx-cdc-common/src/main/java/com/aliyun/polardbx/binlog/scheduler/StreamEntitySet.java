/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class StreamEntitySet extends TreeSet<StreamEntity> {
    public StreamEntitySet() {
    }

    public StreamEntitySet(Comparator<? super StreamEntity> comparator) {
        super(comparator);
    }

    public StreamEntitySet(Collection<? extends StreamEntity> c) {
        super(c);
    }

    public StreamEntitySet(SortedSet<StreamEntity> s) {
        super(s);
    }

    public StreamEntity getStreamEntityWithMaxTimestamp() {
        List<StreamEntity> sortedSourceStreams = this.stream().sorted(
                Comparator.comparingLong(StreamEntity::getTimestamp).thenComparing(StreamEntity::getStreamName))
            .collect(Collectors.toList());
        return sortedSourceStreams.get(sortedSourceStreams.size() - 1);
    }
}
