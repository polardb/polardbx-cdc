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
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

/**
 * 生成recover tso的方式
 *
 * @author yudong
 * @since 2022/11/15 14:55
 **/
public enum RecoverTsoType {
    /**
     * 从binlog_oss_record表中提取recover tso
     */
    BINLOG_RECORD,
    /**
     * 从binlog_system_config表中的latest_cursor字段提取recover tso
     */
    LATEST_CURSOR,
    /**
     * 关闭recover tso功能
     */
    NULL;

    public static RecoverTsoType typeOf(String name) {
        for (RecoverTsoType typeEnum : values()) {
            if (typeEnum.name().equalsIgnoreCase(name)) {
                return typeEnum;
            }
        }
        return NULL;
    }
}
