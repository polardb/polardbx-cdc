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
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ProcessorContext {

    private static final Logger log = LoggerFactory.getLogger("searchLogger");

    private final AuthenticationInfo authenticationInfo;
    /**
     * 记录当前所有未complete的事务，用以记录事务缓存，同时如果找到了合适的位点，当前事务，则从返回当前缓存最小的位点，避免丢失数据。
     */
    private final SortTranMap unCompleteTranMap;
    /**
     * 收到的 commit event
     */
    private final Map<String, Long> commitXidMap = Maps.newHashMap();
    /**
     * 当前处理event log position
     */
    private LogPosition logPosition;
    /**
     * 当前处理事务
     */
    private TranPosition currentTran;
    /**
     * 收到的 command
     */
    private TranPosition commandTran;
    /**
     * 最近一次收到的TSO/GCN
     */
    private Long lastTSO;
    private Long searchTSO;
    private Long baseTSO;
    /**
     * 读取到了cdc 的物理DB 建表语句？ 估计是打标漏掉了
     */
    private boolean isReceivedCreateCdcPhyDbEvent;
    private boolean find = false;
    private boolean preSelected = false;
    private boolean interrupt = false;
    private BinlogPosition findPos;
    private String currentFile;
    private boolean inQuickMode = false;

    public ProcessorContext(AuthenticationInfo authenticationInfo, Long searchTSO, Long baseTSO) {
        this.authenticationInfo = authenticationInfo;
        this.searchTSO = searchTSO;
        this.baseTSO = baseTSO;
        this.unCompleteTranMap = new SortTranMap(baseTSO);
    }

    public BinlogPosition getPosition() {
        // 如果收到commit，没有begin，说明大事务包含了这个位点，只要找到这个事务来做位点
        if (!find || !commitXidMap.isEmpty()) {
            return null;
        }

        return findPos;
    }

    public void onFileComplete() {
        // 忽略素有为complete的事务
        this.unCompleteTranMap.clear();
        // 文件结束清理当前事务
        this.currentTran = null;
        this.interrupt = false;
        this.lastTSO = null;
    }

    public String printDetail() {
        return "lost start size : " + commitXidMap.size() + "[" + JSON.toJSONString(commitXidMap) + "]"
            + "  , lost commit size : " + (unCompleteTranMap.size());
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    private void markCommitEvent(String xid) {
        if (inQuickMode) {
            return;
        }
        if (searchTSO != null && searchTSO > 0) {
            // 搜索tso ， 小于searchTSO ，不需要mark
            if (lastTSO != null && lastTSO <= searchTSO) {
                return;
            }
        } else {
            // 搜索command 模式， 已经搜索到command 不需要mark
            if (commandTran != null) {
                return;
            }
        }
        commitXidMap.put(xid, lastTSO);

    }

    private void searchTSO(TranPosition tranPosition) {
        Long curTso = tranPosition.getTso();
        if (baseTSO != null && baseTSO > 0 && curTso < baseTSO) {
            return;
        }
        String rtso = tranPosition.buildRTso();
        // 场景1
        if (curTso < searchTSO) {
            setPreSelected(rtso);
            if (inQuickMode) {
                setFind(rtso);
                //快速模式，如果当前TSo > searchTSO，直接返回.
                setInterrupt(true);
            }
        } else if (isPreSelected()) {
            setFind(rtso);
            if (inQuickMode) {
                //快速模式，如果当前TSo > searchTSO，直接返回.
                setInterrupt(true);
            }
        } else {
            //收到 cdc DDL 还没有找到command 或者tso ，可能是情况4， 直接返回。
            if (isReceivedCreateCdcPhyDbEvent()) {
                setFind(rtso);
                onFileComplete();
                log.info("find receive crate cdc db position, start pos @ " + getPosition());
                setInterrupt(true);
            }

            if (inQuickMode) {
                //快速模式，如果当前TSo > searchTSO，直接返回.
                setInterrupt(true);
            }

        }
    }

    private void searchCmd() {

        TranPosition commandTran = getCommandTran();
        if (commandTran == null) {
            return;
        }

        if (commandTran.isCdcStartCmd() && CommonUtils.isCdcStartCommandIdMatch(commandTran.getCommandId())) {
            long commitTSO = commandTran.getTso();
            if (commitTSO <= 0) {
                // command tran not commit,will return
                return;
            }
            setFind(commandTran.buildRTso());
            // 比command tso 还小的commit ，就不记录了.
            // 如果tso为null ，考虑可能有xa_start < cmd < xa_command, 那么这个事务和cmd后的事务没有偏序关系，可以忽略
            commitXidMap.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= commitTSO);
            log.warn("remove tso less than  commit TSO , commit size : + " + commitXidMap.size());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Long> stringLongEntry : commitXidMap.entrySet()) {
                sb.append("xid:").append(stringLongEntry.getKey()).append(",tso:")
                    .append(stringLongEntry.getValue()).append("\n");
            }
            log.warn("block transaction: " + sb);
            log.info("find cdc start pos @ " + getPosition());
        }

    }

    /**
     * 停止条件
     * 1、 收到第一个>=searchTSO的event， 取上一个合法位点返回。
     * 2、 收到 storageChangeCommand 且新增加的是当前storage , 直接返回(这个可以用receiveCreateCdcPhyDbEvent就可以返回)。
     * 3、 收到 cdcStartCommand 直接返回
     * 4？、 storageHistory 表中有当前storage记录，且对应的commandId 在command 表中有ADD_STORAGE 指令，则查找到的第一个>=searchTSO直接返回。
     **/
    private void afterCommit(TranPosition tranPosition) {

        if (searchTSO != null && searchTSO != -1) {
            searchTSO(tranPosition);
        } else {
            searchCmd();
        }
    }

    /**
     * xa事务完成后，进行位点判断处理
     */
    public void onComplete(String xid, BinlogPosition end) {
        TranPosition tranPosition = unCompleteTranMap.get(xid);
        if (tranPosition == null) {
            // 没有读到start，记录到commit queue, 此处只处理searchTSO 或者cmdTSO 之后的记录
            if (isAfterTargetTSO()) {
                markCommitEvent(xid);
            }
            return;
        }
        tranPosition.setTso(lastTSO);
        if (lastTSO != null) {
            afterCommit(tranPosition);
        }
        tranPosition.complete(end);

    }

    /**
     * 当前tso 在可选取位点之后
     */
    private boolean isAfterTargetTSO() {
        if (searchTSO > 0) {
            if (lastTSO != null && searchTSO < lastTSO) {
                return true;
            }
        } else {
            if (commandTran == null ||  //当前没有读到commandTran
                commandTran.getTso() == null ||   // 当前 command 没有commit, 可能和lastTSO 有空洞关系，所以也需要记录
                (lastTSO != null
                    && commandTran.getTso() < lastTSO)) { // 当前 command tso 小于 刚刚收到的tso, 说明 lastTSO是处于command 之后提交
                return true;
            }
        }
        return false;
    }

    public void onStart(TranPosition tranPosition) {
        Long tso = commitXidMap.remove(tranPosition.getXid());

        if (find) {
            // 已经找到位点了，到这里可能只是匹配大事务， 需要更新pos为当前Pos
            // 如果tso有值，说明肯定是夸文件了，这里匹配下，commit集合空了可以直接返回
            if (tso != null) {
                if (commitXidMap.isEmpty()) {
                    // 匹配上了，可以退出搜索位点。
                    BinlogPosition beginPos = tranPosition.getBegin();
                    beginPos.setTso(tso);
                    try {
                        beginPos.setRtso(CommonUtils.generateTSO(tso, StringUtils
                            .rightPad(
                                LogEventUtil.getTranIdFromXid(tranPosition.getXid(), authenticationInfo.getCharset())
                                    + "",
                                29,
                                "0"), null));
                    } catch (Exception e) {
                        throw new PolardbxException("build begin pos error", e);
                    }
                    this.setFindPos(beginPos);
                    this.setInterrupt(true);
                    return;
                }
            }
        }
        this.currentTran = tranPosition;
        this.unCompleteTranMap.add(tranPosition);
    }

    public boolean completeTranPos(String xid) {
        return unCompleteTranMap.remove(xid);
    }

    public LogPosition getLogPosition() {
        return logPosition;
    }

    public void setLogPosition(LogPosition logPosition) {
        this.logPosition = logPosition;
    }

    public TranPosition getCurrentTran() {
        return currentTran;
    }

    public void setCurrentTran(TranPosition currentTran) {
        this.currentTran = currentTran;
    }

    public TranPosition getCommandTran() {
        return commandTran;
    }

    public void setCommandTran(TranPosition commandTran) {
        this.commandTran = commandTran;
    }

    public Long getLastTSO() {
        return lastTSO;
    }

    public void setLastTSO(Long lastTSO) {
        this.lastTSO = lastTSO;
    }

    public boolean isReceivedCreateCdcPhyDbEvent() {
        return isReceivedCreateCdcPhyDbEvent;
    }

    public void setReceivedCreateCdcPhyDbEvent(boolean receivedCreateCdcPhyDbEvent) {
        isReceivedCreateCdcPhyDbEvent = receivedCreateCdcPhyDbEvent;
    }

    public boolean isFind() {
        return find;
    }

    public void setFind(String rtso) {
        if (this.findPos == null) {
            this.findPos = unCompleteTranMap.getMinPos(rtso);
            log.warn("set pos : " + this.findPos);
        }
        // 如果当前设置有效位点时，发现pos为null，则可能是在整个binlog刚开始的位置，应该以下一个pos为位点返回。
        if (findPos != null) {
            this.find = true;
        }
    }

    public boolean isPreSelected() {
        return preSelected;
    }

    public void setPreSelected(String rtso) {
        this.findPos = unCompleteTranMap.getMinPos(rtso);
        preSelected = true;
    }

    public boolean isInterrupt() {
        return interrupt;
    }

    public void setInterrupt(boolean interrupt) {
        this.interrupt = interrupt;
    }

    public BinlogPosition getFindPos() {
        return findPos;
    }

    public void setFindPos(BinlogPosition findPos) {
        String rTso =
            this.findPos.getRtso().compareTo(findPos.getRtso()) < 0 ? this.findPos.getRtso() : findPos.getRtso();
        // 取最小值
        if (this.findPos.compareTo(findPos) > 0) {
            this.findPos = findPos;
        }
        this.findPos.setRtso(rTso);
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public void setInQuickMode(boolean inQuickMode) {
        this.inQuickMode = inQuickMode;
    }

}
