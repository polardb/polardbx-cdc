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
package com.aliyun.polardbx.binlog.cdc.topology;

import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import org.apache.commons.lang3.StringUtils;

/**
 * created by ziyang.lb
 **/
public class LowerCaseUtil {

    public static String toLowerCase(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return input.toLowerCase();
    }

    public static void toLowerCaseTopologyRecord(TopologyRecord topologyRecord) {
        if (topologyRecord == null) {
            return;
        }

        if (topologyRecord.getLogicDbMeta() != null) {
            toLowerCaseLogicDbTopology(topologyRecord.getLogicDbMeta());
        }
        if (topologyRecord.getLogicTableMeta() != null) {
            toLowerCaseLogicTableMetaTopology(topologyRecord.getLogicTableMeta());
        }
        topologyRecord.setLowerCased(true);
    }

    public static void toLowerCaseLogicMetaTopology(LogicMetaTopology topology) {
        if (topology == null) {
            return;
        }
        if (topology.getLogicDbMetas() != null) {
            topology.getLogicDbMetas().forEach(LowerCaseUtil::toLowerCaseLogicDbTopology);
        }
        topology.setLowerCased(true);
    }

    public static void toLowerCaseLogicDbTopology(LogicMetaTopology.LogicDbTopology logicDbTopology) {
        if (logicDbTopology == null) {
            return;
        }

        String newSchema = toLowerCase(logicDbTopology.getSchema());
        logicDbTopology.setSchema(newSchema);
        if (logicDbTopology.getLogicTableMetas() != null) {
            logicDbTopology.getLogicTableMetas().forEach(LowerCaseUtil::toLowerCaseLogicTableMetaTopology);
        }
    }

    public static void toLowerCaseLogicTableMetaTopology(
        LogicMetaTopology.LogicTableMetaTopology logicTableMetaTopology) {
        if (logicTableMetaTopology == null) {
            return;
        }
        String newSchema = toLowerCase(logicTableMetaTopology.getTableName());
        logicTableMetaTopology.setTableName(newSchema);
    }
}
