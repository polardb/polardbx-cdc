/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.gtid;

import java.io.IOException;

/**
 * Created by hiwjd on 2018/4/23.
 */
public interface GTIDSet {

    /**
     * 序列化成字节数组
     */
    byte[] encode() throws IOException;

    /**
     * 更新当前实例
     */
    void update(String str);
}
