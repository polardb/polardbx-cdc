/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class ProcessorContext {

    private final AuthenticationInfo authenticationInfo;
    /**
     * 记录当前所有为complete的事务，用以记录事务缓存，同时如果找到了合适的位点，当前事务，则从返回当前缓存最小的位点，避免丢失数据。
     */
    private final SortTranMap unCompleteTranPos = new SortTranMap();
    /**
     * 收到的 commit event
     */
    private final Map<String, Long> commitXidMap = Maps.newHashMap();
    private final Set<String> startXidSet = Sets.newHashSet();
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

    public ProcessorContext(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    public BinlogPosition getPosition() {
        // 如果收到commit，没有begin，说明大事务包含了这个位点，只要找到这个事务来做位点
        if (!find || !commitXidMap.isEmpty()) {
            return null;
        }

        return findPos;
    }

    public void setPreSelected() {
        this.findPos = unCompleteTranPos.getMinPos(lastTSO);
        preSelected = true;
    }

    public void setFind() {
        if (this.findPos == null) {
            this.findPos = unCompleteTranPos.getMinPos(lastTSO);
        }
        // 如果当前设置有效位点时，发现pos为null，则可能是在整个binlog刚开始的位置，应该以下一个pos为位点返回。
        if (findPos != null) {
            this.find = true;
        }
    }

    public void setFindPos(BinlogPosition findPos) {
        // 取最小值
        if (this.findPos.compareTo(findPos) > 0) {
            this.findPos = findPos;
        }
    }

    public void onFileComplete() {
        // 忽略素有为complete的事务
        this.unCompleteTranPos.clear();
        // 不关心为complete的start事务
        this.startXidSet.clear();
        // 文件结束清理当前事务
        this.currentTran = null;
        this.interrupt = false;
        this.lastTSO = null;
    }

    public String printDetial() {
        String detail =
            "lost start size : " + commitXidMap.size() + " , lost commit size : " + (startXidSet.size()
                + unCompleteTranPos.size());
        return detail;
    }
}
