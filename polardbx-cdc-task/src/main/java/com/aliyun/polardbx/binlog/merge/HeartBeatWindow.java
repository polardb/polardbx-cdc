/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_CHECK_HEARTBEAT_WINDOW_ENABLED;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class HeartBeatWindow {

    private static final AtomicLong SEQ = new AtomicLong(0);
    private static final boolean checkHeartBeatWindow =
        DynamicApplicationConfig.getBoolean(TASK_MERGE_CHECK_HEARTBEAT_WINDOW_ENABLED);

    private final long txnId;
    private final String actualTso;
    private final long snapshotSeq;
    private final long seq;
    private final int expectTokenSize;
    private final HashMap<String, MergeItem> heartbeatMergeItems;
    private boolean forceComplete;
    private boolean isDirty;

    public HeartBeatWindow(long txnId, String actualTso, long snapshotSeq, int expectTokenSize) {
        this.txnId = txnId;
        this.actualTso = actualTso;
        this.snapshotSeq = snapshotSeq;
        this.expectTokenSize = expectTokenSize;
        this.seq = SEQ.incrementAndGet();
        this.heartbeatMergeItems = new HashMap<>();
    }

    public void addHeartbeatToken(String sourceId, MergeItem item) {
        if (heartbeatMergeItems.containsKey(sourceId) && checkHeartBeatWindow) {
            String message = "duplicate heartbeat token for merge source: " + sourceId + " , with merge item: " + item;
            if (isDirty) {
                throw new PolardbxException(message);
            } else {
                // 如果该心跳窗口还未变为dirty状态，则允许出现重复
                // 比如在DN发生变配后，可能会触发CDC从oss消费binlog，在从oss模式切换到direct模式时，可能出现重复(recover tso正好对应的是一个心跳时)
                log.warn(message);
            }
        }

        heartbeatMergeItems.put(sourceId, item);
    }

    public boolean isSameWindow(TxnToken token) {
        // 对tso和txnId进行双重验证
        String otherActualTso = CommonUtils.getActualTso(token.getTso());
        return StringUtils.equals(actualTso, otherActualTso) && txnId == token.getTxnId();
    }

    public void forceComplete() {
        forceComplete = true;
    }

    public boolean isComplete() {
        return forceComplete || heartbeatMergeItems.size() == expectTokenSize;
    }

    public String getActualTso() {
        return actualTso;
    }

    public Collection<TxnToken> getAllHeartBeatTokens() {
        return heartbeatMergeItems.values().stream().map(MergeItem::getTxnToken).collect(Collectors.toList());
    }

    public Collection<MergeItem> getAllMergeItems() {
        return heartbeatMergeItems.values();
    }

    public long getSeq() {
        return seq;
    }

    public long getSnapshotSeq() {
        return snapshotSeq;
    }

    public boolean isForceComplete() {
        return forceComplete;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    @Override
    public String toString() {
        return "HeartBeatWindow{" +
            "txnId='" + txnId + '\'' +
            ", actualTso='" + actualTso + '\'' +
            ", snapshotSeq=" + snapshotSeq +
            ", seq=" + seq +
            ", expectTokenSize=" + expectTokenSize +
            ", heartbeatMergeItems=" + heartbeatMergeItems +
            ", forceComplete=" + forceComplete +
            ", isDirty=" + isDirty +
            '}';
    }
}
