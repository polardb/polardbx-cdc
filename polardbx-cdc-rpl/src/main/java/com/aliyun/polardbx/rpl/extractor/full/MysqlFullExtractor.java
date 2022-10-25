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
package com.aliyun.polardbx.rpl.extractor.full;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.filter.DataImportFilter;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * @author shicai.xsc 2020/12/6 19:10
 * @since 5.0.0.0
 */
@Slf4j
public class MysqlFullExtractor extends BaseExtractor {

    protected Map<String, DataSource> dataSourceMap;
    protected FullExtractorConfig extractorConfig;
    protected ExecutorService executorService;
    protected List<MysqlFullProcessor> runningProcessors;
    protected List<Future> runningFetchTasks;
    protected List<Future> runningCountTasks;
    protected HostInfo hostInfo;
    protected String extractorName;
    protected DataImportFilter filter;

    public MysqlFullExtractor(FullExtractorConfig extractorConfig, HostInfo hostInfo, DataImportFilter filter) {
        super(extractorConfig);
        this.extractorName = "MysqlFullExtractor";
        this.extractorConfig = extractorConfig;
        this.hostInfo = hostInfo;
        this.filter = filter;
    }

    @Override
    public boolean init() throws Exception {
        try {
            log.info("initializing {}", extractorName);
            super.init();
            dataSourceMap = new HashMap<>();
            for (String db : filter.getDoDbs()) {
                DataSource dataSource =
                    DataSourceUtil.createDruidMySqlDataSource(hostInfo.isUsePolarxPoolCN(), hostInfo.getHost(),
                        hostInfo.getPort(),
                        db,
                        hostInfo.getUserName(),
                        hostInfo.getPassword(),
                        "",
                        1,
                        extractorConfig.getParallelCount(),
                        null,
                        null);
                dataSourceMap.put(db, dataSource);
            }
            executorService = ThreadPoolUtil.createExecutorWithFixedNum(
                extractorConfig.getParallelCount(),
                extractorName);
            runningProcessors = new ArrayList<>();
            runningFetchTasks = new ArrayList<>();
            runningCountTasks = new ArrayList<>();
        } catch (Throwable e) {
            log.error("failed to init {}", extractorName, e);
            throw e;
        }
        return true;
    }

    @Override
    public void start() throws Exception {
        log.info("starting {}", extractorName);

        // We support change ignoreTable after the service created
        Set<String> dbNames = filter.getDoDbs();
        Set<String> ignoreTables = filter.getIgnoreTables();
        for (String dbName : dbNames) {
            for (String tbName : filter.getDoTables().get(dbName)) {
                if (ignoreTables.contains(tbName)) {
                    log.info("tbName:{} ignored", tbName);
                    continue;
                }

                MysqlFullProcessor processor = new MysqlFullProcessor();
                processor.setExtractorConfig(extractorConfig);
                processor.setDataSource(dataSourceMap.get(dbName));
                processor.setSchema(dbName);
                processor.setTbName(tbName);
                processor.setLogicalSchema(filter.getRewriteDb(dbName, null));
                processor.setLogicalTbName(filter.getRewriteTable(tbName));
                processor.setHostInfo(hostInfo);
                processor.setPipeline(pipeline);
                // 获得所有的待全量的表的行数，用来计算进度
                Future future = executorService.submit(() -> processor.preStart());
                runningCountTasks.add(future);
                runningProcessors.add(processor);
                log.info("{} submit for schema:{}, tbName:{}", extractorName, dbName, tbName);
            }
        }

        for (Future future : runningCountTasks) {
            future.get();
        }
        for (MysqlFullProcessor processor: runningProcessors) {
            Future future = executorService.submit(() -> processor.start());
            runningFetchTasks.add(future);
        }
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future future : runningFetchTasks) {
            allDone &= future.isDone();
        }
        // here if alldone but !isAllDataTransferred()
        // need to exit process to retry tasks failed
        if (allDone)  {
            if (isAllDataTransfered()) {
                return true;
            } else {
                Runtime.getRuntime().halt(1);
            }
        }
        return false;
    }

    public boolean isAllDataTransfered() {
        Set<String> ignoreTables = filter.getIgnoreTables();
        Set<String> dbNames = filter.getDoDbs();
        for (String dbName : dbNames) {
            for (String tbName : filter.getDoTables().get(dbName)) {
                if (ignoreTables.contains(tbName)) {
                    continue;
                }

                String fullTableName = dbName + "." + tbName;
                RplDbFullPosition position = DbTaskMetaManager
                    .getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
                if (position == null || position.getFinished() != RplConstants.FINISH) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void stop() {
        log.warn("stopping {}", extractorName);
    }
}
