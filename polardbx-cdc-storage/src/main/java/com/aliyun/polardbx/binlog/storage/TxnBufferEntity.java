/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * created by ziyang.lb
 **/
@NoArgsConstructor
@Data
public class TxnBufferEntity implements Serializable {
    public TxnKey txnKey;
    public AtomicBoolean started;
    public AtomicBoolean completed;
    public AtomicInteger subSequenceGenerator;
    public LinkedList<TxnItemRef> refList;
    public long memSize;
    public int itemSizeBeforeMerge;
    public String lastTraceId;
    public volatile boolean hasPersistingData;
    public volatile boolean shouldPersist;
    public volatile boolean restored;
    public transient Iterator<TxnItemRef> iterator;

    @SneakyThrows
    public byte[] serialize() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        try {
            oos.writeObject(this);
            oos.flush();
            return bos.toByteArray();
        } finally {
            oos.close();
            bos.close();
        }
    }

    @SneakyThrows
    public static TxnBufferEntity deserialize(byte[] bytes) {
        Object obj;
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        try {
            obj = ois.readObject();
            return (TxnBufferEntity) obj;
        } finally {
            ois.close();
            bis.close();
        }
    }
}
