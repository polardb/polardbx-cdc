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
