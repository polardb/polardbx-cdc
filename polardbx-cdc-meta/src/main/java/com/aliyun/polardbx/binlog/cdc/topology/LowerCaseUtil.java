/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
