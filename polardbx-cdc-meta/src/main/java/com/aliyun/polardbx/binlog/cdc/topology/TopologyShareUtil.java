/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_WITH_INTERN;
import static com.aliyun.polardbx.binlog.cdc.topology.LowerCaseUtil.toLowerCaseLogicMetaTopology;

/**
 * created by ziyang.lb
 **/
public class TopologyShareUtil {
    private static final boolean NEED_SHARE_FLAG = parseSwitch();

    private static final ConcurrentHashMap<String, LogicMetaTopology> snapshotCache = new ConcurrentHashMap<>();

    public static LogicMetaTopology buildTopology(String tso, Supplier<LogicMetaTopology> supplier) {
        if (needShareString()) {
            return snapshotCache.computeIfAbsent(tso, k -> toShare(supplier.get())).copy();
        } else {
            LogicMetaTopology topology = supplier.get();
            toLowerCaseLogicMetaTopology(topology);
            return topology;
        }
    }

    private static boolean parseSwitch() {
        return DynamicApplicationConfig.getBoolean(META_BUILD_SHARE_TOPOLOGY_ENABLED);
    }

    public static boolean needShareString() {
        return NEED_SHARE_FLAG;
    }

    public static boolean needIntern() {
        return needShareString() && DynamicApplicationConfig.getBoolean(META_BUILD_SHARE_TOPOLOGY_WITH_INTERN);
    }

    public static LogicMetaTopology toShare(LogicMetaTopology topology) {
        topology.setShared(true);
        toLowerCaseLogicMetaTopology(topology);
        if (needIntern()) {
            internTopology(topology);
        }
        return topology;
    }

    public static void internTopologyRecord(TopologyRecord topologyRecord) {
        if (topologyRecord.getLogicDbMeta() != null) {
            internLogicDb(topologyRecord.getLogicDbMeta());
        }
        if (topologyRecord.getLogicTableMeta() != null) {
            internLogicTable(topologyRecord.getLogicTableMeta());
        }
    }

    public static void internTopology(LogicMetaTopology topology) {
        if (topology == null) {
            return;
        }
        topology.getLogicDbMetas().forEach(TopologyShareUtil::internLogicDb);
        topology.setInterned(true);
    }

    private static void internLogicDb(LogicMetaTopology.LogicDbTopology t) {
        t.setSchema(internOneString(t.getSchema()));
        t.setCharset(internOneString(t.getCharset()));
        if (t.getPhySchemas() != null) {
            t.getPhySchemas().forEach(p -> {
                p.setGroup(internOneString(p.getGroup()));
                p.setSchema(internOneString(p.getSchema()));
                p.setStorageInstId(internOneString(p.getStorageInstId()));
            });
        }
        if (t.getLogicTableMetas() != null) {
            t.getLogicTableMetas().forEach(TopologyShareUtil::internLogicTable);
        }
    }

    private static void internLogicTable(LogicMetaTopology.LogicTableMetaTopology m) {
        m.setTableName(internOneString(m.getTableName()));
        m.setTableCollation(internOneString(m.getTableCollation()));
        m.setCreateSql(internOneString(m.getCreateSql()));
        m.setCreateSql4Phy(internOneString(m.getCreateSql4Phy()));
        if (m.getPhySchemas() != null) {
            m.getPhySchemas().forEach(p -> {
                p.setStorageInstId(internOneString(p.getStorageInstId()));
                p.setSchema(internOneString(p.getSchema()));
                p.setGroup(internOneString(p.getGroup()));
                if (p.getPhyTables() != null) {
                    p.setPhyTables(p.getPhyTables().stream().map(String::intern).collect(Collectors.toList()));
                }
            });
        }
    }

    private static String internOneString(String input) {
        if (StringUtils.isNotBlank(input)) {
            return input.intern();
        }
        return input;
    }
}
