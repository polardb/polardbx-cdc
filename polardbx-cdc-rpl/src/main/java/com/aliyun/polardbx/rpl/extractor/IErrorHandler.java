/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

/**
 * @author chengjin.lyf on 2018/3/19 下午7:31
 * @since 3.2.6
 */
public interface IErrorHandler {
    boolean handle(Throwable throwable);
}
