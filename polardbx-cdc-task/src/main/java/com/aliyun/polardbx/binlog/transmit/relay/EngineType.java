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
package com.aliyun.polardbx.binlog.transmit.relay;

/**
 * create by ziyang.lb
 **/
public enum EngineType {
    /**
     * 普通文件，顺序读写性能最好
     */
    FILE,
    /**
     * rocksdb，操作简便快捷，写速度快，读性能稍差
     */
    ROCKSDB,
    /**
     * 随机模式，实验室测试使用
     */
    RANDOM
}
