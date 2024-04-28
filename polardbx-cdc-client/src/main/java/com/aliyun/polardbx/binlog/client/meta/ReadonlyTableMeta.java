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
package com.aliyun.polardbx.binlog.client.meta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.client.MetaDbHelper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * @author yanfenglin
 */
public class ReadonlyTableMeta extends MemoryTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(ReadonlyTableMeta.class);
    private MetaDbHelper metaDbHelper;
    private volatile boolean isInit = false;

    public ReadonlyTableMeta(MetaDbHelper metaDbHelper) {
        super(logger, false);
        this.metaDbHelper = metaDbHelper;
    }

    public void rollback(String rollbackTso) throws SQLException {
        logger.info("begin to rollback table meta to " + rollbackTso);
        List<Map<String, Object>> baseMetaDataList = metaDbHelper.selectLatestSnapshotLogicMetaHistory(rollbackTso);
        if (baseMetaDataList.isEmpty()) {
            throw new PolardbxException("cdc base binlog logic meta history not find");
        }
        Map<String, Object> baseRowMap = baseMetaDataList.get(0);
        LogicMetaTopology logicMetaTopology =
            JSON.parseObject((String) baseRowMap.get("topology"), LogicMetaTopology.class);
        for (LogicMetaTopology.LogicDbTopology topology : logicMetaTopology.getLogicDbMetas()) {
            String schema = topology.getSchema();
            String charset = topology.getCharset();
            repository.setDefaultSchemaWithCharset(schema, charset);
            for (LogicMetaTopology.LogicTableMetaTopology metaTopology : topology.getLogicTableMetas()) {
                String ddl = metaTopology.getCreateSql();
                apply(null, schema, ddl, null);
            }
        }

        String baseTSO = (String) baseRowMap.get("tso");
        String snapshotTSO = baseTSO;
        final int PAGE_SIZE = 5;

        while (true) {
            final String snapshotTsoCondition = snapshotTSO;
            List<Map<String, Object>> dataList =
                metaDbHelper.selectLogicMetaDDLList(snapshotTsoCondition, rollbackTso, PAGE_SIZE);
            for (Map<String, Object> historyRowMap : dataList) {
                String ddl = (String) historyRowMap.get("ddl");
                String schemaName = (String) historyRowMap.get("db_name");
                apply(null, schemaName, ddl, null);
            }
            int size = dataList.size();
            if (size == PAGE_SIZE) {
                snapshotTSO = (String) dataList.get(size - 1).get("tso");
            } else {
                break;
            }
        }
        this.isInit = true;
        logger.info("success to rollback table meta to " + rollbackTso);
    }

    public boolean isInit() {
        return isInit;
    }
}
