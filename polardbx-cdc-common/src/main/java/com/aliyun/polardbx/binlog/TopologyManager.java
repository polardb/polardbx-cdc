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
package com.aliyun.polardbx.binlog;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologyManager {

    protected String rules;
    protected Map<String, String> phy2logic = new HashMap();
    protected Map<String, List<String>> suffix2phyTableMap = new HashMap<>();
    protected Set<String> logicTableSet = new HashSet<>();

    public TopologyManager(String rules) {
        this.rules = rules;
        this.parse();
    }

    protected void parse() {

    }


    public List<String> getAllPhyTableList(String phyDbName) {
        return new ArrayList<>();
    }

    /**
     * 从logicTableSet 集合中选取对应的物理表，并且，如果无法从规则中读取则直接提取对应表到返回结果中
     */
    public List<String> getAllPhyTableList(String phyDbName, Set<String> logicTableSet) {
        return new ArrayList<>();
    }

    public String getLogicTable(String phyTableName) {
        return phy2logic.get(phyTableName);
    }

    public Set<String> difference(Set<String> logicTableSet) {
        return Sets.difference(logicTableSet, this.logicTableSet);
    }

    public Set<String> getLogicTableSet() {
        return logicTableSet;
    }
}
