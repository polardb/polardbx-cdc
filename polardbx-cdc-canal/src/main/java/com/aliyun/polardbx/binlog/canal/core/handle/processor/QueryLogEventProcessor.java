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
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.canal.system.SystemDB.LOGIC_SCHEMA;

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
            // 直接重置一把
//            context.setLastTSO(null);

            if (searchTSO == -1) {
                // 刚刚初始化，不需要这个判断
                return;
            }
            if (event.getQuery().startsWith("XA")) {
                return;
            }
            try {
                String ddl = event.getQuery();
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DDL_REMOVEHINTS_SUPPORT)) {
                    ddl = com.aliyun.polardbx.binlog.canal.core.ddl.SQLUtils.removeDDLHints(ddl);
                }
                SQLStatementParser parser =
                    SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
                List<SQLStatement> statementList = parser.parseStatementList();
                SQLStatement statement = statementList.get(0);

                if (statement instanceof SQLCreateDatabaseStatement) {
                    SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                    String databaseName1 = SQLUtils.normalize(createDatabaseStatement.getDatabaseName());
                    String databaseName2 = getCdcPhyDbNameByStorageInstId(context.getAuthenticationInfo());
                    if (StringUtils.equalsIgnoreCase(databaseName1, databaseName2)) {
                        context.setReceivedCreateCdcPhyDbEvent(true);
                        logger
                            .info("receive create sql for cdc physical database, sql content is : " + event.getQuery());
                    }
                }
            } catch (Exception e) {
                logger.error("try parse ddlSql failed : " + event.getQuery());
            }
        }
    }

    private String getCdcPhyDbNameByStorageInstId(AuthenticationInfo authenticationInfo) {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = jdbcTemplate.queryForList(String.format(
            "select g.phy_db_name from db_group_info g,group_detail_info d where "
                + "d.group_name = g.group_name and d.inst_id = '%s' and d.storage_inst_id = '%s' and d.db_name = '%s'",
            DynamicApplicationConfig.getString(POLARX_INST_ID), authenticationInfo.getStorageInstId(), LOGIC_SCHEMA),
            String.class);
        return list.isEmpty() ? "" : list.get(0);
    }
}
