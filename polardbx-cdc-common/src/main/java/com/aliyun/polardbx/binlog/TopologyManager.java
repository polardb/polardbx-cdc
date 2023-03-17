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
package com.aliyun.polardbx.binlog;

import java.util.List;
import java.util.Set;

public interface TopologyManager {

    /**
     * 从logicTableSet 集合中选取对应的物理表，并且，如果无法从规则中读取则直接提取对应表到返回结果中
     */
    List<String> getAllPhyTableList(String phyDbName, Set<String> logicTableSet);

    String getLogicTable(String phyTableName);

    Set<String> getLogicTableSet();
}
