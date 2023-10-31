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
