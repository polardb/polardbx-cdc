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
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author chengjin.lyf on 2020/7/23 2:02 下午
 * @since 1.0.25
 */
public class RuntimeContext {
    public static final AtomicReference<String> initTopology = new AtomicReference<>("");
    private static final ThreadLocal<String> instructionIdLocal = new ThreadLocal<>();
    private final ThreadRecorder threadRecorder;

    /**
     * 物理binlog被tso分成几段，tso之间自己排序， 两个tso内部以物理顺序为主，
     * 且内部tso都以当前已收到的最大tso为 基准tso，
     * 同时保障txnId 始终大于等于当前最大tso 对应的txnId。
     */
    private final TsoSegment tsoSegment = new TsoSegment();
    private final Map<Integer, Object> attributeMap = new HashMap<>();
    private AuthenticationInfo authenticationInfo;

    private String binlogFile;
    private long logPos;
    private String hostAddress;
    private String version;
    private String sqlMode;
    private BinlogPosition startPosition;
    /**
     * 是否位点回溯启动
     */
    private boolean recovery;
    private long serverId;
    private int lowerCaseTableNames;
    private ServerCharactorSet serverCharactorSet;
    private String storageHashCode;
    private Long dnTransferMaxTSOBarrier;

    public RuntimeContext(ThreadRecorder threadRecorder) {
        this.threadRecorder = threadRecorder;
    }

    public static String getInitTopology() {
        return initTopology.get();
    }

    public static void setInitTopology(String topology) {
        initTopology.compareAndSet("", topology);
    }

    public static String getInstructionId() {
        return instructionIdLocal.get();
    }

    public static void setInstructionId(String instructionId) {
        instructionIdLocal.set(instructionId);
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public void setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.storageHashCode = Objects.hashCode(this.authenticationInfo.getStorageMasterInstId()) + "";
    }

    public void markBindDnTransferMaxTSOBarrier() {
        this.dnTransferMaxTSOBarrier = getMaxTSO();
    }

    public void cleanDnTransferTSOBarrier() {
        this.dnTransferMaxTSOBarrier = null;
    }

    public Long getDnTransferMaxTSOBarrier() {
        return dnTransferMaxTSOBarrier;
    }

    public void putAttribute(Integer id, Object attribute) {
        attributeMap.put(id, attribute);
    }

    public Object getAttribute(Integer id) {
        return attributeMap.get(id);
    }

    public String getStorageInstId() {
        return authenticationInfo.getStorageMasterInstId();
    }

    public ThreadRecorder getThreadRecorder() {
        return threadRecorder;
    }

    public String getDefaultDatabaseCharset() {
        return serverCharactorSet.getCharacterSetDatabase();
    }

    public ServerCharactorSet getServerCharactorSet() {
        return serverCharactorSet;
    }

    public void setServerCharactorSet(ServerCharactorSet serverCharactorSet) {
        this.serverCharactorSet = serverCharactorSet;
    }

    public int nextMaxTxnIdSequence(long txnId) {
        return this.tsoSegment.nextSeq(txnId);
    }

    public long getMaxTxnId() {
        return tsoSegment.getTxnId();
    }

    public Long getMaxTSO() {
        return this.tsoSegment.getTso();
    }

    public void setMaxTSO(Long newTSO, Long newTxnId) {
        this.tsoSegment.trySet(newTSO, newTxnId);
    }

    public boolean isRecovery() {
        return recovery;
    }

    public void setRecovery(boolean recovery) {
        this.recovery = recovery;
    }

    public BinlogPosition getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(BinlogPosition startPosition) {
        this.startPosition = startPosition;
    }

    public boolean hasTSO() {
        return this.tsoSegment.isTsoAvaliable();
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public long getLogPos() {
        return logPos;
    }

    public void setLogPos(long logPos) {
        this.logPos = logPos;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSqlMode() {
        return sqlMode;
    }

    public void setSqlMode(String sqlMode) {
        this.sqlMode = sqlMode;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public int getLowerCaseTableNames() {
        return lowerCaseTableNames;
    }

    public void setLowerCaseTableNames(int lowerCaseTableNames) {
        this.lowerCaseTableNames = lowerCaseTableNames;
    }

    public String getStorageHashCode() {
        return storageHashCode;
    }
}
