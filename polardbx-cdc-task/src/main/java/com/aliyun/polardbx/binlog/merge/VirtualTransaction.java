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

import java.util.LinkedList;
import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class VirtualTransaction implements MergeTransaction {

    /**
     * 一批连续的单机事务 or 一阶段XA事务 or 不要进行合并的NoTsoXA事务的partial
     */
    private final List<TxnToken> tokens;

    public VirtualTransaction(TxnToken token) {
        this();
        this.appendToken(token);
    }

    public VirtualTransaction() {
        tokens = new LinkedList<>();
    }

    public void appendToken(TxnToken token) {
        tokens.add(token);
    }

    public List<TxnToken> tokens() {
        return tokens;
    }
}
