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

/**
 * Created by ziyang.lb ,2020-07-18
 **/
public interface Storage {

    /**
     * 顾名思义，启动storage
     */
    void start();

    /**
     * 顾名思义，停止storage
     */
    void stop();

    /**
     * 创建TxnBuffer实例，如果已经存在，则抛异常
     */
    TxnBuffer create(TxnKey key) throws AlreadyExistException;

    /**
     * 获取TxnBuffer实例
     *
     * @param key 事务标识符
     * @return 事务缓存，如果不存在返回null，否则返回已缓存的对象
     */
    TxnBuffer fetch(TxnKey key);

    /**
     * 将某个事务从Storage中移除，如事务回滚时、事务数据已经被消费完成时
     *
     * @param key 事务标识符
     */
    void delete(TxnKey key);

    /**
     * 将某个事务从Storage中移除，如事务回滚时、事务数据已经被消费完成时，异步操作
     *
     * @param key 事务标识符
     */
    void deleteAsync(TxnKey key);

    /**
     * 判断TxnBuffer是否存在
     *
     * @param key 事务标识符
     * @return 对应的TxnBuffer是否存在，true-存在，false-不存在
     */
    boolean exist(TxnKey key);

    /**
     * 获取正在等待被清理的TxnKeyGroup的个数
     */
    long getCleanerQueuedSize();

}
