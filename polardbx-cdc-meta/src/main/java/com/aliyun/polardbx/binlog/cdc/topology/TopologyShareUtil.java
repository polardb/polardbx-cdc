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
package com.aliyun.polardbx.binlog.cdc.topology;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_TOPOLOGY_SHARE_SWITCH;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_TOPOLOGY_SHARE_USE_INTERN;
import static com.aliyun.polardbx.binlog.cdc.topology.LowerCaseUtil.toLowerCaseLogicMetaTopology;

/**
 * created by ziyang.lb
 **/
public class TopologyShareUtil {
    private final static String SHARE_SWITCH_ON = "ON";
    private final static String SHARE_SWITCH_OFF = "OFF";
    private final static String SHARE_SWITCH_RANDOM = "RANDOM";//for test
    private final static boolean NEED_SHARE_FLAG = parseSwitch();

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
        String sw = DynamicApplicationConfig.getString(META_TOPOLOGY_SHARE_SWITCH);
        if (SHARE_SWITCH_ON.equals(sw)) {
            return true;
        } else if (SHARE_SWITCH_OFF.equals(sw)) {
            return false;
        } else if (SHARE_SWITCH_RANDOM.equals(sw)) {
            return new Random().nextBoolean();
        } else {
            throw new PolardbxException("invalid intern switch config : " + sw);
        }
    }

    public static boolean needShareString() {
        return NEED_SHARE_FLAG;
    }

    public static boolean needIntern() {
        return needShareString() && DynamicApplicationConfig.getBoolean(META_TOPOLOGY_SHARE_USE_INTERN);
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
