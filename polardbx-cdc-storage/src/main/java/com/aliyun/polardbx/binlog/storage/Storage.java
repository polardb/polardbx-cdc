/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

    /**
     * 获取仓库对象
     */
    Repository getRepository();
}
