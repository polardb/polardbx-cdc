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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public class TopologyManagerValidator implements TopologyManager {

    private TopologyManager targetRule;
    private TopologyManager targetDrds;

    public TopologyManagerValidator(String rules, String ip, int port, String dbName, String username,
                                    String password) {
        try {
            Constructor<?> tmConstructor =
                Class.forName("com.aliyun.polardbx.binlog.TopologyManagerImpl").getConstructor(String.class);
            targetRule = (TopologyManager) tmConstructor.newInstance(rules);
        } catch (Exception e) {
            throw new PolardbxException("init tddl rules topology failed !", e);
        }
        targetDrds = new DrdsTopologyManagerImpl(ip, port, dbName, username, password);
    }

    /**
     * 从logicTableSet 集合中选取对应的物理表，并且，如果无法从规则中读取则直接提取对应表到返回结果中
     */
    @Override
    public List<String> getAllPhyTableList(String phyDbName, Set<String> logicTableSet) {
        List<String> ruleList = targetRule.getAllPhyTableList(phyDbName, logicTableSet);
        List<String> drdsList = targetDrds.getAllPhyTableList(phyDbName, logicTableSet);
        log.info("ruleList size: {}, drdsList size: {, {}}",  ruleList.size(), drdsList.size());
        if (ruleList.size() != drdsList.size()) {
            List<String> ruleListDiff = new ArrayList<>(ruleList);
            ruleListDiff.removeAll(drdsList);
            List<String> drdsListDiff = new ArrayList<>(drdsList);
            drdsListDiff.removeAll(ruleList);
            log.error("in ruleList, not in drdsList: {}", ruleListDiff);
            log.error("in drdsList, not in ruleList: {}", drdsListDiff);
            throw new PolardbxException(
                "parser rule tables size not equal drds show tables size, rule table size :" + ruleList.size()
                    + " , drds show tables size :" + drdsList.size());
        }
        return drdsList;
    }

    @Override
    public String getLogicTable(String phyTableName) {
        return targetDrds.getLogicTable(phyTableName);
    }

    @Override
    public Set<String> getLogicTableSet() {
        return targetDrds.getLogicTableSet();
    }
}
