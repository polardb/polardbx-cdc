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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.LowerCaseTableNameVariables;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.EventReformater;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.util.RegexUtil;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX;

public class QueryEventReformator implements EventReformater<QueryLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("rebuildEventLogger");

    private final PolarDbXTableMetaManager tableMetaManager;

    public QueryEventReformator(PolarDbXTableMetaManager tableMetaManager) {
        this.tableMetaManager = tableMetaManager;
    }

    @Override
    public Set<Integer> interest() {
        Set<Integer> idSet = new HashSet<>();
        idSet.add(LogEvent.QUERY_EVENT);
        return idSet;
    }

    @Override
    public boolean accept(QueryLogEvent event) {
        String query = event.getQuery();
        if (LogEventUtil.isTransactionEvent(event)) {
            return false;
        }

        if (SystemDB.isSys(event.getDbName())) {
            // ignore 系统库 DDL
            return false;
        }

        // 配置黑名单方式过滤解析失败、或不需要处理的queryEvent,比如:grant、savepoint等
        if (ignoreQueryEvent(query)) {
            logger.warn("ignore phy ddl : " + query);
            return false;
        }
        return true;
    }

    @Override
    public void register(Map<Integer, EventReformater> map) {
        map.put(LogEvent.QUERY_EVENT, this);
    }

    @Override
    public boolean reformat(QueryLogEvent event, TxnItemRef txnItemRef, ReformatContext context, EventData eventData)
        throws Exception {
        String query = event.getQuery();

        if (context.getLowerCaseTableNames() == LowerCaseTableNameVariables.LOWERCASE.getValue()) {
            query = query.toLowerCase();
        }

        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_REMOVE_HINTS_IN_DDL_SQL)) {
            query = com.aliyun.polardbx.binlog.canal.core.ddl.SQLUtils.removeDDLHints(query);
        }

        BinlogPosition position = new BinlogPosition(context.getBinlogFile(),
            event.getLogPos(),
            event.getServerId(),
            event.getWhen());
        position.setRtso(context.getVirtualTSO());

        logger.info("receive phy ddl " + query + " for pos " + JSONObject.toJSONString(position));
        boolean useHistoryTableFirst = DynamicApplicationConfig.getBoolean(META_BUILD_APPLY_FROM_HISTORY_FIRST);
        if (useHistoryTableFirst) {
            logger.warn("begin to query ddl sql from history table for db {} and tso {}.", event.getDbName(),
                position.getRtso());
            String tempSql = getPhySqlFromHistoryTable(event.getDbName(), position.getRtso(),
                context.getStorageInstanceId());
            if (org.apache.commons.lang.StringUtils.isNotBlank(tempSql)) {
                query = tempSql;
                logger.warn("ddl sql in history table is " + query);
            } else {
                logger.warn("ddl sql is not existed in history table, schema name {}, position {}, origin sql {}",
                    event.getDbName(), position.getRtso(), query);
            }
        }
        tableMetaManager.applyPhysical(position, event.getDbName(), query, null);
        return false;
    }

    private String getPhySqlFromHistoryTable(String dbName, String tso, String storageInstId) {
        BinlogPhyDdlHistoryMapper mapper = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        List<BinlogPhyDdlHistory> ddlHistories = mapper.select(
            s -> s
                .where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isEqualTo(tso))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.dbName, SqlBuilder.isEqualTo(dbName))
        );
        return ddlHistories.isEmpty() ? null : ddlHistories.get(0).getDdl();
    }

    private Boolean ignoreQueryEvent(String query) {
        if (StringUtils.isBlank(query)) {
            return true;
        }
        return RegexUtil.match(
            DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX), query.trim());
    }
}
