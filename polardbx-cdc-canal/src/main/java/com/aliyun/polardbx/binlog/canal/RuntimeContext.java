/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chengjin.lyf on 2020/7/23 2:02 下午
 * @since 1.0.25
 */
public class RuntimeContext {

    public static final int DEBUG_MODE = 0;
    public static final int ATTRIBUTE_TABLE_META_MANAGER = 1;

    private AuthenticationInfo authenticationInfo;
    /**
     * 当前收到的最大TSO
     * no TSO事务，全部使用maxTSO
     */
    private Long maxTSO = 0L;

    private String binlogFile;
    private ThreadRecorder threadRecorder;
    private long logPos;
    private String hostAddress;
    private String version;

    private BinlogPosition startPosition;
    private String topology;
    /**
     * 是否位点回溯启动
     */
    private boolean recovery;

    private int serverId;

    private int lowerCaseTableNames;

    private ServerCharactorSet serverCharactorSet;

    /**
     * 最大事务ID 序号生成
     * 用来为 no XA事务生成事务ID，要求seq必须大于前一个事务ID
     */
    private AutoSequence maxTxnIdSequence = new AutoSequence();

    private Map<Integer, Object> attributeMap = new HashMap<>();

    public RuntimeContext(ThreadRecorder threadRecorder) {
        this.threadRecorder = threadRecorder;
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public void setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    public void putAttribute(Integer id, Object attribute) {
        attributeMap.put(id, attribute);
    }

    public Object getAttribute(Integer id) {
        return attributeMap.get(id);
    }

    public String getStorageInstId() {
        return authenticationInfo.getStorageInstId();
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
        return this.maxTxnIdSequence.nextSeq(txnId);
    }

    public long getMaxTxnId() {
        return maxTxnIdSequence.maxValue();
    }

    public void setMaxTxnId(long txnId) {
        this.maxTxnIdSequence.setMaxValue(txnId);
    }

    public Long getMaxTSO() {
        return maxTSO;
    }

    public void setMaxTSO(Long maxTSO) {
        if (this.maxTSO == null) {
            this.maxTSO = maxTSO;
        } else {
            this.maxTSO = Math.max(maxTSO, this.maxTSO);
        }
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
        return maxTSO != null;
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

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public int getLowerCaseTableNames() {
        return lowerCaseTableNames;
    }

    public void setLowerCaseTableNames(int lowerCaseTableNames) {
        this.lowerCaseTableNames = lowerCaseTableNames;
    }
}
