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
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Created by ziyang.lb
 **/
public class MergeItem implements Comparable<MergeItem> {

    private String sourceId;
    private TxnToken txnToken;
    private LinkedList<TxnToken> txnTokenList;
    private MergeSource mergeSource;

    public MergeItem() {
    }

    public MergeItem(String sourceId, TxnToken txnToken, MergeSource mergeSource) {
        this.sourceId = sourceId;
        this.txnToken = txnToken;
        this.mergeSource = mergeSource;
    }

    public MergeItem(String sourceId, MergeSource mergeSource) {
        this.sourceId = sourceId;
        this.mergeSource = mergeSource;
        this.txnTokenList = new LinkedList<>();
    }

    public String getSourceId() {
        return sourceId;
    }

    public TxnToken getTxnToken() {
        if (txnTokenList != null) {
            return txnTokenList.getLast();
        } else {
            return txnToken;
        }
    }

    public Collection<TxnToken> getAllTxnTokens() {
        if (txnTokenList != null) {
            return txnTokenList;
        } else {
            return Lists.newArrayList(txnToken);
        }
    }

    public MergeSource getMergeSource() {
        return mergeSource;
    }

    public void addTxnToken(TxnToken token) {
        txnTokenList.add(token);
    }

    @Override
    public int compareTo(MergeItem o) {
        TxnToken thisToken = getTxnToken();
        TxnToken thatToken = o.getTxnToken();
        return thisToken.getTso().compareTo(thatToken.getTso());
    }
}
