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

package com.aliyun.polardbx.binlog.transfer;

public class Transfer {

    private final int src;
    private final int dst;
    private final int amount;

    public Transfer(int src, int dst, int amount) {
        this.src = src;
        this.dst = dst;
        this.amount = amount;
    }

    public int getSrc() {
        return src;
    }

    public int getDst() {
        return dst;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Transfer{" + "src=" + src + ", dst=" + dst + ", amount=" + amount + '}';
    }
}
