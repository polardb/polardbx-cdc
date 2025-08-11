/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.RegexUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_REMOVE_HINTS_IN_DDL_SQL;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.LOGIC_SCHEMA;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * begin
 * xa begin
 * xa commit
 */
public class QueryLogEventProcessor implements ILogEventProcessor<QueryLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("searchLogger");
    private final ILogEventProcessor XAStartProcessor;
    private final ILogEventProcessor XACommitProcessor;
    private final ILogEventProcessor XARollbackProcessor;
    private final long searchTSO;

    public QueryLogEventProcessor(long searchTSO, ILogEventProcessor XAStartProcessor,
                                  ILogEventProcessor XACommitProcessor,
                                  ILogEventProcessor XARollbackProcessor) {
        this.searchTSO = searchTSO;
        this.XAStartProcessor = XAStartProcessor;
        this.XACommitProcessor = XACommitProcessor;
        this.XARollbackProcessor = XARollbackProcessor;
    }

    @Override
    public void handle(QueryLogEvent event, ProcessorContext context) {
        if (LogEventUtil.isStart(event)) {
            XAStartProcessor.handle(event, context);
        } else if (XACommitProcessor != null && LogEventUtil.isCommit(event)) {
            XACommitProcessor.handle(event, context);
        } else if (XARollbackProcessor != null && LogEventUtil.isRollback(event)) {
            XARollbackProcessor.handle(event, context);
        } else {
            if (searchTSO == -1) {
                // 刚刚初始化，不需要这个判断
                return;
            }
            if (event.getQuery().startsWith("XA")) {
                return;
            }
            try {
                String ddl = event.getQuery();
                if (getBoolean(TASK_EXTRACT_REMOVE_HINTS_IN_DDL_SQL)) {
                    ddl = com.aliyun.polardbx.binlog.canal.core.ddl.SQLUtils.removeDDLHints(ddl);
                }

                boolean ignore = RegexUtil.match(getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX), ddl);
                if (ignore) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("ignore ddl sql in searching stage, ddl is {}, log position is {}",
                            ddl, context.getLogPosition());
                    }
                    return;
                }

                SQLStatement statement = parseSQLStatement(ddl);
                if (statement instanceof SQLCreateDatabaseStatement) {
                    SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                    String databaseName1 = SQLUtils.normalize(createDatabaseStatement.getDatabaseName());
                    String databaseName2 = getCdcPhyDbNameByStorageInstId(context.getAuthenticationInfo());
                    if (StringUtils.equalsIgnoreCase(databaseName1, databaseName2)) {
                        BinlogPosition position = new BinlogPosition(context.getLogPosition().getFileName(),
                            event.getLogPos() - event.getEventLen(), event.getServerId(), event.getWhen());
                        position.setRtso(CommonUtils.generateTSO(searchTSO, StringUtils.rightPad("",
                            29, "0"), context.getAuthenticationInfo().getStorageInstId()));
                        context.setReceivedCreateCdcPhyDbEvent(true, position);
                        logger.info("receive create sql for cdc physical database, sql content is : "
                            + event.getQuery() + ", will use pos : " + position);
                    }
                }
            } catch (Exception e) {
                logger.error("try parse ddlSql failed, log position : {}, sql content : {}. ",
                    event.getLogPos(), event.getQuery(), e);
            }
        }
    }

    private String getCdcPhyDbNameByStorageInstId(AuthenticationInfo authenticationInfo) {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = jdbcTemplate.queryForList(String.format(
                "select g.phy_db_name from db_group_info g,group_detail_info d where "
                    + "d.group_name = g.group_name and d.storage_inst_id = '%s' and d.db_name = '%s'",
                authenticationInfo.getStorageMasterInstId(),
                LOGIC_SCHEMA),
            String.class);
        return list.isEmpty() ? "" : list.get(0);
    }
}
