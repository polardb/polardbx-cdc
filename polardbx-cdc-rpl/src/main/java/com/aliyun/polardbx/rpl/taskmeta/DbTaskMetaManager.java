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

package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.PolarxCNodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.PolarxCNodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.RplDdlDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplDdlMapper;
import com.aliyun.polardbx.binlog.dao.RplServiceDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplServiceMapper;
import com.aliyun.polardbx.binlog.dao.RplStateMachineDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplStateMachineMapper;
import com.aliyun.polardbx.binlog.dao.RplTablePositionDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplTablePositionMapper;
import com.aliyun.polardbx.binlog.dao.RplTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTablePosition;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.AbstractFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.render.RenderingStrategies;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author shicai.xsc 2020/12/29 13:04
 * @since 5.0.0.0
 */
@Slf4j
public class DbTaskMetaManager {

    private static RplStateMachineMapper stateMachineMapper = SpringContextHolder
        .getObject(RplStateMachineMapper.class);
    private static RplServiceMapper serviceMapper = SpringContextHolder.getObject(RplServiceMapper.class);
    private static RplDdlMapper ddlMapper = SpringContextHolder.getObject(RplDdlMapper.class);
    private static RplTaskMapper taskMapper = SpringContextHolder.getObject(RplTaskMapper.class);
    private static NodeInfoMapper nodeInfoMapper = SpringContextHolder
        .getObject(NodeInfoMapper.class);
    private static PolarxCNodeInfoMapper polarxCNodeInfoMapper = SpringContextHolder
        .getObject(PolarxCNodeInfoMapper.class);
    private static RplTablePositionMapper tablePositionMapper =
        SpringContextHolder.getObject(RplTablePositionMapper.class);

    /************************* state machine *******************/
    public static RplStateMachine createStateMachine(String config, StateMachineType type, AbstractFSM fsm,
                                                     String channel, String clusterId, String context) {
        RplStateMachine record = new RplStateMachine();
        record.setConfig(config);
        record.setStatus(StateMachineStatus.STOPPED.getValue());
        record.setType(type.getValue());
        record.setClassName(fsm.getClass().getName());
        record.setState(fsm.getInitialState().getValue());
        record.setClusterId(clusterId);
        record.setContext(context);
        switch (type) {
        case REPLICA:
            record.setChannel(channel);
            break;
        default:
            break;
        }
        stateMachineMapper.insert(record);
        return record;
    }

    public static List<RplStateMachine> listStateMachine(StateMachineStatus status, String clusterId) {
        return stateMachineMapper
            .selectMany(SqlBuilder.select(RplStateMachineDynamicSqlSupport.rplStateMachine.allColumns())
                .from(RplStateMachineDynamicSqlSupport.rplStateMachine)
                .where(RplStateMachineDynamicSqlSupport.status, SqlBuilder.isEqualTo(status.getValue()))
                .and(RplStateMachineDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .build()
                .render(RenderingStrategies.MYBATIS3));
    }

    public static List<RplStateMachine> listRplStateMachine() {
        return stateMachineMapper
            .selectMany(SqlBuilder.select(RplStateMachineDynamicSqlSupport.rplStateMachine.allColumns())
                .from(RplStateMachineDynamicSqlSupport.rplStateMachine)
                .where(RplStateMachineDynamicSqlSupport.type, SqlBuilder.isEqualTo(StateMachineType.REPLICA.getValue()))
                .build()
                .render(RenderingStrategies.MYBATIS3));
    }

    public static RplStateMachine getRplStateMachine(String channel) {
        Optional<RplStateMachine> res = stateMachineMapper
            .selectOne(i -> i.where(RplStateMachineDynamicSqlSupport.channel, SqlBuilder.isEqualTo(channel))
                .and(RplStateMachineDynamicSqlSupport.type, SqlBuilder.isEqualTo(StateMachineType.REPLICA.getValue())));
        return res.isPresent() ? res.get() : null;
    }

    public static RplStateMachine getStateMachine(long id) {
        Optional<RplStateMachine> res = stateMachineMapper
            .selectOne(i -> i.where(RplStateMachineDynamicSqlSupport.id, SqlBuilder.isEqualTo(id)));
        return res.isPresent() ? res.get() : null;
    }

    public static RplStateMachine updateStateMachine(RplStateMachine record) {
        stateMachineMapper.updateByPrimaryKeySelective(record);
        return getStateMachine(record.getId());
    }

    public static RplStateMachine updateStateMachineStatus(long id, StateMachineStatus status) {
        RplStateMachine record = new RplStateMachine();
        record.setId(id);
        record.setStatus(status.getValue());
        stateMachineMapper.updateByPrimaryKeySelective(record);
        return getStateMachine(id);
    }

    public static void updateStateMachineContext(long id, String context) {
        RplStateMachine record = new RplStateMachine();
        record.setId(id);
        record.setContext(context);
        stateMachineMapper.updateByPrimaryKeySelective(record);
    }

    public static RplStateMachine updateStateMachineState(long id, FSMState state) {
        RplStateMachine record = getStateMachine(id);
        if (record == null) {
            return null;
        }
        log.info("Update state machine state [id : {}] from : {} to: {}",
            id, FSMState.from(record.getState()).name(), state.name());
        RplStateMachine newRecord = new RplStateMachine();
        newRecord.setId(id);
        newRecord.setState(state.getValue());
        stateMachineMapper.updateByPrimaryKeySelective(newRecord);
        return getStateMachine(id);
    }

    public static void deleteStateMachine(long id) {
        stateMachineMapper.deleteByPrimaryKey(id);
        List<RplService> services = listService(id);
        for (RplService service : services) {
            DbTaskMetaManager.deleteService(service.getId());
        }
        deleteTablePositionByFsm(id);
        deleteDdlByFSM(id);
    }

    /************************* service *************************/

    public static RplService addService(RplService record) {
        serviceMapper.insert(record);
        return record;
    }

    public static RplService addService(long stateMachineId, ServiceType type, List<FSMState> stateList) {
        RplService record = new RplService();
        record.setStateMachineId(stateMachineId);
        record.setStatus(ServiceStatus.NULL.getValue());
        record.setServiceType(type.getValue());
        record.setStateList(FSMState.listToString(stateList));
        serviceMapper.insert(record);
        return record;
    }

    public static RplService getService(long id) {
        Optional<RplService> res = serviceMapper
            .selectOne(i -> i.where(RplServiceDynamicSqlSupport.id, SqlBuilder.isEqualTo(id)));
        return res.isPresent() ? res.get() : null;
    }

    public static RplService getService(long stateMachineId, ServiceType type) {
        Optional<RplService> res = serviceMapper
            .selectOne(i -> i.where(RplServiceDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
                .and(RplServiceDynamicSqlSupport.serviceType, SqlBuilder.isEqualTo(type.getValue())));
        return res.isPresent() ? res.get() : null;
    }

    public static List<RplService> listService(long stateMachineId) {
        return serviceMapper.selectMany(SqlBuilder.select(RplServiceDynamicSqlSupport.rplService.allColumns())
            .from(RplServiceDynamicSqlSupport.rplService)
            .where(RplServiceDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
            .orderBy(RplServiceDynamicSqlSupport.id)
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    public static List<RplService> listRplService() {
        return serviceMapper.selectMany(SqlBuilder.select(RplServiceDynamicSqlSupport.rplService.allColumns())
            .from(RplServiceDynamicSqlSupport.rplService)
            .where(RplServiceDynamicSqlSupport.serviceType, SqlBuilder.isEqualTo(ServiceType.REPLICA.getValue()))
            .orderBy(RplServiceDynamicSqlSupport.id)
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    public static RplService updateService(long id, ServiceStatus status) {
        RplService record = new RplService();
        record.setId(id);
        record.setStatus(status.getValue());
        serviceMapper.updateByPrimaryKeySelective(record);
        return getService(id);
    }

    public static RplService updateService(RplService record) {
        serviceMapper.updateByPrimaryKeySelective(record);
        return getService(record.getId());
    }

    public static void deleteService(long serviceId) {
        serviceMapper.deleteByPrimaryKey(serviceId);
        taskMapper.delete(i -> i.where(RplTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(serviceId)));
    }

    /************************* task ***************************/

    public static RplTask addTask(long stateMachineId, long serviceId,
                                  String extractorConfig, String pipelineConfig, String applierConfig,
                                  ServiceType type, int sequenceId, String clusterId) {
        RplTask record = new RplTask();
        record.setStateMachineId(stateMachineId);
        record.setServiceId(serviceId);
        record.setStatus(ServiceStatus.NULL.getValue());
        record.setType(type.getValue());
        record.setExtractorConfig(extractorConfig);
        record.setPipelineConfig(pipelineConfig);
        record.setApplierConfig(applierConfig);
        record.setLastError("");
        record.setPosition("");
        record.setClusterId(clusterId);
        record.setExtra(Integer.toString(sequenceId));
        taskMapper.insert(record);
        return record;
    }

    public static RplTask getTask(long id) {
        Optional<RplTask> res = taskMapper
            .selectOne(i -> i.where(RplServiceDynamicSqlSupport.id, SqlBuilder.isEqualTo(id)));
        return res.isPresent() ? res.get() : null;
    }

    public static List<RplTask> listTask(long serviceId) {
        return taskMapper.selectMany(SqlBuilder.select(RplTaskDynamicSqlSupport.rplTask.allColumns())
            .from(RplTaskDynamicSqlSupport.rplTask)
            .where(RplTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(serviceId))
            .orderBy(RplTaskDynamicSqlSupport.id)
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    public static List<RplTask> listClusterTask(TaskStatus status, String clusterId) {
        return taskMapper.selectMany(SqlBuilder.select(RplTaskDynamicSqlSupport.rplTask.allColumns())
            .from(RplTaskDynamicSqlSupport.rplTask)
            .where(RplTaskDynamicSqlSupport.status, SqlBuilder.isEqualTo(status.getValue()))
            .and(RplTaskDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    public static List<RplTask> listTask(String worker, TaskStatus status, String clusterId) {
        return taskMapper.selectMany(SqlBuilder.select(RplTaskDynamicSqlSupport.rplTask.allColumns())
            .from(RplTaskDynamicSqlSupport.rplTask)
            .where(RplTaskDynamicSqlSupport.status, SqlBuilder.isEqualTo(status.getValue()))
            .and(RplTaskDynamicSqlSupport.worker, SqlBuilder.isEqualTo(worker))
            .and(RplTaskDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    public static RplTask updateTask(long id, TaskStatus status, String worker, String position, String statistic,
                                     Date gmtHeartbeat) {
        RplTask record = new RplTask();
        record.setId(id);
        if (status != null) {
            record.setStatus(status.getValue());
        }
        record.setWorker(worker);
        record.setPosition(position);
        record.setStatistic(statistic);
        record.setGmtHeartbeat(gmtHeartbeat);
        taskMapper.updateByPrimaryKeySelective(record);
        return getTask(id);
    }

    public static RplTask updateTaskLastError(long id, String lastError) {
        RplTask record = new RplTask();
        record.setId(id);
        record.setLastError(lastError);
        taskMapper.updateByPrimaryKeySelective(record);
        return getTask(id);
    }

    public static RplTask updateTaskStatus(long id, TaskStatus status) {
        return updateTask(id, status, null, null, null, null);
    }

    public static RplTask updateTaskConfig(long id, String extractorConfig, String pipelineConfig,
                                           String applierConfig) {
        RplTask record = new RplTask();
        record.setId(id);
        record.setExtractorConfig(extractorConfig);
        record.setPipelineConfig(pipelineConfig);
        record.setApplierConfig(applierConfig);
        taskMapper.updateByPrimaryKeySelective(record);
        return getTask(id);
    }

    public static RplTask updateTaskWorker(long id, String worker) {
        return updateTask(id, null, worker, null, null, null);
    }

    public static RplTask updateBinlogPosition(long id, String position) {
        return updateTask(id, null, null, position, null, null);
    }

    public static RplTask updateExtra(long id, String extra) {
        RplTask record = new RplTask();
        record.setId(id);
        record.setExtra(extra);
        taskMapper.updateByPrimaryKeySelective(record);
        return getTask(id);
    }

    public static RplTask clearHistory(long id) {
        RplTask record = new RplTask();
        record.setId(id);
        record.setPosition("");
        record.setLastError("");
        record.setStatistic("");
        taskMapper.updateByPrimaryKeySelective(record);
        return record;
    }

    public static void deleteAfterTasks(long stateMachineId, long curServiceId) {
        taskMapper.delete(i -> i.where(RplTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
            .and(RplTaskDynamicSqlSupport.serviceId, SqlBuilder.isGreaterThan(curServiceId)));
    }

    public static void deleteTask(long id) {
        taskMapper.deleteByPrimaryKey(id);
    }

    /************************ workers ********************/
    public static List<NodeInfo> listRunningWorker(String cluster, int status) {
        return nodeInfoMapper.selectMany(SqlBuilder.select(NodeInfoDynamicSqlSupport.nodeInfo.allColumns())
            .from(NodeInfoDynamicSqlSupport.nodeInfo)
            .where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(cluster))
            .and(NodeInfoDynamicSqlSupport.gmtModified,
                SqlBuilder.isGreaterThan(DateTime.now().minusSeconds(RplConstants.WORKER_KEEP_ALIVE_SECONDS).toDate()))
            .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(status))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    /************************ ddl ********************/
    public static RplDdl getDdl(String ddlTso) {
        Optional<RplDdl> res = ddlMapper
            .selectOne(i -> i.where(RplDdlDynamicSqlSupport.ddlTso, SqlBuilder.isEqualTo(ddlTso)));
        return res.isPresent() ? res.get() : null;
    }

    public static void updateDdl(RplDdl ddl) {
        ddlMapper.updateByPrimaryKeySelective(ddl);
    }

    public static RplDdl addDdl(RplDdl ddl) {
        ddlMapper.insert(ddl);
        return ddl;
    }

    public static void deleteDdlByFSM(long FSMId) {
        ddlMapper.delete(i -> i.where(RplDdlDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(FSMId)));
    }

    public static void deleteDdlByTask(long taskId) {
        ddlMapper.delete(i -> i.where(RplDdlDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId)));
    }

    /************************* table position ***************************/
    public static void updateTablePosition(long stateMachineId, long serviceId, long taskId, String fullTableName,
                                           String position) {
        Optional<RplTablePosition> res = tablePositionMapper
            .selectOne(i -> i.where(RplTablePositionDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
                .and(RplTablePositionDynamicSqlSupport.fullTableName, SqlBuilder.isEqualTo(fullTableName)));
        RplTablePosition record;
        if (res.isPresent()) {
            record = new RplTablePosition();
            record.setId(res.get().getId());
            record.setPosition(position);
            tablePositionMapper.updateByPrimaryKeySelective(record);
        } else {
            record = new RplTablePosition();
            record.setFullTableName(fullTableName);
            record.setPosition(position);
            record.setStateMachineId(stateMachineId);
            record.setServiceId(serviceId);
            record.setTaskId(taskId);
            tablePositionMapper.insert(record);
        }
    }

    public static void deleteTablePositionByFsm(long stateMachineId) {
        tablePositionMapper.delete(i -> i.where(RplTablePositionDynamicSqlSupport.stateMachineId,
            SqlBuilder.isEqualTo(stateMachineId)));
    }

    public static void deleteTablePositionByTask(long taskId) {
        tablePositionMapper.delete(i -> i.where(RplTablePositionDynamicSqlSupport.taskId,
            SqlBuilder.isEqualTo(taskId)));
    }

    public static List<RplTablePosition> listTablePosition(long taskId) {
        return tablePositionMapper
            .selectMany(SqlBuilder.select(RplTablePositionDynamicSqlSupport.rplTablePosition.allColumns())
                .from(RplTablePositionDynamicSqlSupport.rplTablePosition)
                .where(RplTablePositionDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
                .build()
                .render(RenderingStrategies.MYBATIS3));
    }

    /************************* node ***************************/
    public static List<PolarxCNodeInfo> listPolarxCNodeInfo() {
        return polarxCNodeInfoMapper.select(s -> s.where(PolarxCNodeInfoDynamicSqlSupport.gmtModified,
            SqlBuilder.isGreaterThan(DateTime.now().minusMinutes(2).toDate())));
    }


}
