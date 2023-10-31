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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.google.protobuf.ByteString;

/**
 * @author yudong
 * @since 2023/7/19 18:27
 **/
public interface IBinlogReader {
    /**
     * 初始化
     */
    void init();

    /**
     * 判断 binlog dump 请求是否合法
     */
    void valid();

    /**
     * 启动
     */
    void start();

    /**
     * 退出
     */
    void close();

    /**
     * 检查正在读取的文件的状态
     */
    boolean check();

    /**
     * 是否还有数据需要读取
     */
    boolean hasNext();

    /**
     * 返回下一个包
     */
    ByteString nextPack();
}
