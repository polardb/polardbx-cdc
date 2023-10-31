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
package com.aliyun.polardbx.binlog.remote.io;

import java.io.IOException;

public interface IFileReader {

    /**
     * 返回该binlog文件的长度
     *
     * @return file length
     */
    long length();

    /**
     * 返回该binlog文件的文件名
     *
     * @return file name
     */
    String getName();

    /**
     * 将binlog文件的一部分内容读到buffer中
     *
     * @param buffer 缓冲区
     * @return 本地读取的字节数
     */
    int read(byte[] buffer) throws IOException;

    /**
     * 检查该binlog是否已经完成了所有的写入
     */
    boolean isComplete();

    /**
     * 关闭io流
     */
    void close();
}
