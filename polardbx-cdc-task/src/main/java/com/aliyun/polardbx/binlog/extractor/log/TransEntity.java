/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * created by ziyang.lb
 **/
@Data
@NoArgsConstructor
public class TransEntity implements Serializable {

    //common variables
    public String binlogFileName;
    public long startLogPos;
    public long when;
    public Long serverId;
    public String charset;
    public boolean ignore = false;
    public long stopLogPos;
    public boolean isCdcSingle;
    public boolean heartbeat = false;
    public boolean syncPoint = false;
    public String sourceCdcSchema;
    public String groupId;

    //事务&tso
    public TxnKey txnKey;
    public String xid;
    public boolean hasRealXid;
    public Long transactionId;
    public String virtualTsoStr;
    public boolean txGlobal = false;
    public Long txGlobalTso;
    public Long txGlobalTid;
    public boolean xa = false;
    public boolean tsoTransaction = false;
    public long realTso = -1;

    //trace id
    public String nextTraceId;
    public String originalTraceId;
    public String lastTraceId;
    public String lastRowsQuery;

    //format desc event
    public boolean descriptionEvent = false;

    //instruction
    public InstructionType instructionType = null;
    public String instructionContent = null;
    public String instructionId = null;

    // persist flag
    public boolean shouldPersist;

    // 如果为true，sync point 进行assert时忽略
    public boolean syncPointCheckIgnoreFlag = false;

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
    public static TransEntity deserialize(byte[] bytes) {
        Object obj;
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        try {
            obj = ois.readObject();
            return (TransEntity) obj;
        } finally {
            ois.close();
            bis.close();
        }
    }
}
