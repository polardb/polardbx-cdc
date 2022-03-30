/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.TableTypeEnum;
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
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ITableMetaDelegate;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMetaChangeEvent;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMetaChangeListener;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMetaDelegate;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author chengjin.lyf on 2020/7/15 4:40 下午
 * @since 1.0.25
 */
public class EventAcceptFilter implements LogEventFilter<LogEvent>, TableMetaChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(EventAcceptFilter.class);
    private static final String rdsInteralMark = "/* rds internal mark */";
    private String storageInstanceId;
    private Set<Integer> acceptEvent = new HashSet<>();
    private ITableMetaDelegate delegate;
    private boolean ignoreFilterQueryLogEvent;
    /**
     * map<schema,set<table>>
     */
    private Map<String, Set<String>> filter = new HashMap<>();

    public EventAcceptFilter(String storageInstanceId, boolean ignoreFilterQueryLogEvent) {
        this.storageInstanceId = storageInstanceId;
        this.ignoreFilterQueryLogEvent = ignoreFilterQueryLogEvent;
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
        if (rc.getAttribute(RuntimeContext.DEBUG_MODE) != null) {
            delegate = (ITableMetaDelegate) rc.getAttribute(RuntimeContext.ATTRIBUTE_TABLE_META_MANAGER);
            delegate.addTableChangeListener(this);
            return;
        }
        try {
            PolarDbXTableMetaManager polarDbXTableMetaManager = new PolarDbXTableMetaManager(rc.getStorageInstId());
            polarDbXTableMetaManager.init();
            if (rc.isRecovery()) {
                logger.info("start rollback to " + rc.getStartPosition().getRtso());
                polarDbXTableMetaManager.rollback(rc.getStartPosition());
            } else {
                logger.info("start apply base to " + rc.getStartPosition().getRtso());
                polarDbXTableMetaManager.applyBase(rc.getStartPosition(),
                    JSON.parseObject(rc.getTopology(), LogicMetaTopology.class));
            }
            delegate = new TableMetaDelegate(polarDbXTableMetaManager);
            delegate.addTableChangeListener(this);
            rc.putAttribute(RuntimeContext.ATTRIBUTE_TABLE_META_MANAGER, delegate);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    @Override
    public void onStop() {
        if (delegate != null) {
            delegate.destroy();
            delegate = null;
        }
    }

    private boolean isSingleGroup(String groupName) {
        return groupName.contains("SINGLE");
    }

    /**
     * 过滤掉广播和 GSI 表
     */
    private void removeUnAcceptTable(Set<String> phyTables, String schema, String groupName,
                                     TableMetaDelegate delegate) {
        Iterator<String> it = phyTables.iterator();
        while (it.hasNext()) {
            String phyTab = it.next();
            LogicMetaTopology.LogicDbTopology dbTopology = delegate.getLogicSchema(schema, phyTab);
            TableTypeEnum tableTypeEnum = TableTypeEnum.typeOf(dbTopology.getLogicTableMetas().get(0).getTableType());
            switch (tableTypeEnum) {
            case BROADCAST:
                if (!isSingleGroup(groupName)) {
                    it.remove();
                }
                break;
            case GSI:
                it.remove();
                break;
            default:
                // do nothing
            }
        }
    }

    private void refreshFilter() {
        if (logger.isDebugEnabled()) {
            logger.debug("start rebuild table filter : " + new Gson().toJson(filter) + " => " + storageInstanceId);
        }

        this.filter = delegate.buildTableFilter(storageInstanceId);

        if (logger.isDebugEnabled()) {
            logger.debug("success rebuild filter : " + new Gson().toJson(filter) + " => " + storageInstanceId);
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
        if (table == null && filter.containsKey(schema)) {
            return true;
        }
        Set<String> tableList = filter.get(schema);
        if (tableList != null && tableList.contains(table)) {
            return true;
        }
        return false;
    }

    @Override
    public void onTableChange(TableMetaChangeEvent event) {

        if (!event.isTopologyChange()) {
            return;
        }
        // 只要拓扑变了，直接刷新filter
        refreshFilter();
    }
}
