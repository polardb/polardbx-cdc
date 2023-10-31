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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.IFilterBuilder;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_FILTER_LOGIC_DB_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_FILTER_LOGIC_TABLE_BLACKLIST;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildTopology;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

/**
 * @author chengjin.lyf on 2020/7/15 4:40 下午
 * @since 1.0.25
 */
public class EventAcceptFilter implements LogEventFilter<LogEvent>, IFilterBuilder {

    private static final Logger logger = LoggerFactory.getLogger(EventAcceptFilter.class);
    private static final String rdsInteralMark = "/* rds internal mark */";
    private final PolarDbXTableMetaManager tableMetaManager;
    private final Set<String> cdcSchemaSet;
    private String storageInstanceId;
    private Set<Integer> acceptEvent = new HashSet<>();
    private boolean ignoreFilterQueryLogEvent;
    /**
     * map<schema,set<table>>
     */
    private Map<String, Set<String>> filter = new HashMap<>();

    public EventAcceptFilter(String storageInstanceId, boolean ignoreFilterQueryLogEvent,
                             PolarDbXTableMetaManager tableMetaManager, Set<String> cdcSchemaSet) {
        this.storageInstanceId = storageInstanceId;
        this.ignoreFilterQueryLogEvent = ignoreFilterQueryLogEvent;
        this.tableMetaManager = tableMetaManager;
        this.cdcSchemaSet = cdcSchemaSet;
    }

    @Override
    public void handle(LogEvent event, HandlerContext context) throws Exception {
        if (accept(event)) {
            context.doNext(event);
        }
    }

    @Override
    public void onStart(HandlerContext context) {
        RuntimeContext rc = context.getRuntimeContext();
        try {

            if (rc.isRecovery()) {
                logger.info("start rollback to " + rc.getStartPosition().getRtso());
                tableMetaManager.rollback(rc.getStartPosition());
            } else {
                logger.info("start apply base to " + rc.getStartPosition().getRtso());
                tableMetaManager.applyBase(rc.getStartPosition(),
                    buildTopology(ORIGIN_TSO, () -> {
                        if (StringUtils.isBlank(RuntimeContext.getInitTopology())) {
                            throw new PolardbxException("init topology can`t be empty");
                        }
                        return JSON.parseObject(RuntimeContext.getInitTopology(), LogicMetaTopology.class);
                    }), RuntimeContext.getInstructionId());
            }
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    @Override
    public void onStop() {
        if (tableMetaManager != null) {
            tableMetaManager.destroy();
        }
    }

    private boolean isSingleGroup(String groupName) {
        return groupName.contains("SINGLE");
    }

    private void refreshFilter() {
        if (logger.isDebugEnabled()) {
            logger.debug("start rebuild table filter : " + JSONObject.toJSONString(filter)
                + " => " + storageInstanceId);
        }

        this.filter = buildTableFilter();

        if (logger.isDebugEnabled()) {
            logger.debug("success rebuild filter : " + JSONObject.toJSONString(filter) + " => " + storageInstanceId);
        }
    }

    @Override
    public void onStartConsume(HandlerContext context) {
        refreshFilter();
    }

    public void addAcceptEvent(Integer eventType) {
        this.acceptEvent.add(eventType);
    }

    public boolean accept(LogEvent event) {
        if (!acceptEvent.contains(event.getHeader().getType())) {
            return false;
        }
        if (event instanceof RowsLogEvent) {
            TableMapLogEvent tableMapLogEvent = ((RowsLogEvent) event).getTable();
            return accept(tableMapLogEvent.getDbName().toLowerCase(), tableMapLogEvent.getTableName().toLowerCase());
        }
        if (event instanceof TableMapLogEvent) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            return accept(tableMapLogEvent.getDbName().toLowerCase(), tableMapLogEvent.getTableName().toLowerCase());
        }
        if (event instanceof QueryLogEvent) {
            if (ignoreFilterQueryLogEvent) {
                return true;
            }
            QueryLogEvent queryLogEvent = (QueryLogEvent) event;
            String query = queryLogEvent.getQuery();
            if (query.startsWith(rdsInteralMark)) {
                return false;
            }
            return accept(queryLogEvent.getDbName().toLowerCase(), null);
        }
        return true;
    }

    private boolean accept(String schema, String table) {

        if (schema.startsWith("__cdc__")) {
            return cdcSchemaSet.contains(schema);
        }

        if (table == null && filter.containsKey(schema)) {
            return true;
        }
        Set<String> tableList = filter.get(schema);
        if (tableList != null && tableList.contains(table)) {
            return true;
        }
        return false;
    }

    public boolean acceptCdcSchema(String schemaName) {
        return cdcSchemaSet.contains(schemaName);
    }

    @Override
    public void rebuild() {
        refreshFilter();
    }

    private Map<String, Set<String>> buildTableFilter() {
        Set<String> excludeLogicDbs = Sets.newHashSet();
        Set<String> excludeLogicTables = Sets.newHashSet();

        String dbBlackList = DynamicApplicationConfig.getString(TASK_EXTRACT_FILTER_LOGIC_DB_BLACKLIST);
        if (StringUtils.isNotBlank(dbBlackList)) {
            dbBlackList = dbBlackList.toLowerCase();
            String[] array = StringUtils.split(dbBlackList, ",");
            excludeLogicDbs.addAll(Arrays.asList(array));
        }

        String tableBlackList = DynamicApplicationConfig.getString(TASK_EXTRACT_FILTER_LOGIC_TABLE_BLACKLIST);
        if (StringUtils.isNotBlank(tableBlackList)) {
            tableBlackList = tableBlackList.toLowerCase();
            String[] array = StringUtils.split(tableBlackList, ",");
            excludeLogicTables.addAll(Arrays.asList(array));
        }

        List<LogicMetaTopology.PhyTableTopology> phyTableTopologyList =
            tableMetaManager.getPhyTables(storageInstanceId, excludeLogicDbs, excludeLogicTables);
        Map<String, Set<String>> tmpFilter = new HashMap<>();
        phyTableTopologyList.forEach(s -> {
            final Set<String> phyTables = s.getPhyTables()
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            final String schema = s.getSchema().toLowerCase();
            Set<String> tmpTablesSet = tmpFilter.computeIfAbsent(schema, k -> new HashSet<>());
            tmpTablesSet.addAll(phyTables);
        });

        LogicMetaTopology topology = tableMetaManager.getTopology();
        List<LogicMetaTopology.LogicDbTopology> logicDbTopologyList = topology.getLogicDbMetas();
        logicDbTopologyList.forEach(t -> {
            t.getPhySchemas().forEach(phyDbTopology -> {
                if (phyDbTopology.getStorageInstId().equalsIgnoreCase(storageInstanceId)) {
                    String phySchema = phyDbTopology.getSchema().toLowerCase();
                    if (!tmpFilter.containsKey(phySchema)) {
                        tmpFilter.put(phySchema, new HashSet<>());
                    }
                }
            });
        });
        return tmpFilter;
    }
}
