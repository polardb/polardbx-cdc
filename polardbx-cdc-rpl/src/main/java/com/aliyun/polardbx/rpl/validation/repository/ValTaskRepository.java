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
package com.aliyun.polardbx.rpl.validation.repository;

import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.validation.common.Record;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;

import java.sql.SQLException;
import java.util.List;

/**
 * @author siyu.yusi
 * @date 02/28/2022
 **/
public interface ValTaskRepository {
    /**
     * Create validation task records
     */
    void createValTasks(ValidationTypeEnum type);

    /**
     * Count validation task records number
     * @return
     */
    long countValRecords(String srcTable);

    /**
     * Get validation task
     * @param srcPhyTable
     * @return
     * @throws Exception
     */
    ValidationTask getValTaskRecord(String srcPhyTable) throws Exception;

    /**
     * Persis diff rows
     * @param srcTable
     * @param keyRowValList
     * @throws Exception
     */
    void persistDiffRows(TableInfo srcTable, List<Record> keyRowValList) throws Exception;

    /**
     * Update validation task state
     * @param srcTable
     * @param state
     * @throws Exception
     */
    void updateValTaskState(TableInfo srcTable, ValidationStateEnum state) throws Exception;

    /**
     * Get validation task list
     * @return
     */
    List<ValidationTask> getValTaskList();

    /**
     * Get diff list based on src physical table name
     * @param srcTable
     * @return
     */
    List<ValidationDiff> getValDiffList(TableInfo srcTable);

    /**
     * Get validation task with external id
     * @param refId
     * @return
     * @throws SQLException
     */
    ValidationTask getValTaskByRefId(String refId) throws SQLException;
}
