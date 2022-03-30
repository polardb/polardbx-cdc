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

package com.aliyun.polardbx.binlog;

/**
 * created by ziyang.lb
 **/
public abstract class ConfigKeys {

    //******************************************************************************************************************
    //***************************************************系统基础参数配置**************************************************
    //******************************************************************************************************************
    public static final String TASK_NAME = "taskName";
    /**
     * Polarx主实例的id
     */
    public static final String POLARX_INST_ID = "polardbx.instance.id";

    /**
     * polarx实例IP
     */
    public static final String POLARX_URL = "polarx_url";
    /**
     * polarx实例用户名
     */
    public static final String POLARX_USERNAME = "polarx_username";
    /**
     * polarx实例用户密码
     */
    public static final String POLARX_PASSWORD = "polarx_password";
    /**
     * CDC集群ID
     */
    public static final String CLUSTER_ID = "cluster_id";
    /**
     * 当前CDC节点的inst id
     */
    public static final String INST_ID = "ins_id";
    /**
     * 当前CDC节点的ip
     */
    public static final String INST_IP = "ins_ip";
    /**
     * 当前CDC节点的CPU核数
     */
    public static final String CPU_CORES = "cpu_cores";
    /**
     * 当前CDC节点的内存大小，单位：M
     */
    public static final String MEM_SIZE = "mem_size";
    /**
     * 当前CDC节点的磁盘大小，单位：M
     */
    public static final String DISK_SIZE = "disk_size";
    /**
     * daemon进程服务端口
     */
    public static final String DAEMON_PORT = "daemon_port";
    /**
     * 分配给该实例的可用端口
     */
    public static final String COMMON_PORTS = "common_ports";
    /**
     * 当前运行时的模式，Local or Cluster
     */
    public static final String RUNTIME_MODE = "runtime.mode";
    /**
     * 是否打印metrics
     */
    public static final String PRINT_METRICS = "printMetrics";
    /**
     * 激活Daemon接口ACL
     */
    public static final String ENABLE_INTERFACE_ACL = "enableInterfaceACL";
    /**
     * 当前cdc集群类型
     */
    public static final String CLUSTER_TYPE = "cluster_type";
    /**
     * 密码加解密key
     */
    public static final String DN_PASSWORD_KEY = "dnPasswordKey";
    /**
     * 密码配置是否使用密文，默认false
     */
    public static final String USE_ENCRYPTED_PASSWORD = "useEncryptedPassword";
    /**
     * 显示指定DN的ip，特殊情况下使用
     */
    public static final String ASSIGNED_DN_IP = "assignedDnIp";

    //******************************************************************************************************************
    //********************************************逻辑Binlog目录&文件&上传下载等相关配置**************************************
    //******************************************************************************************************************
    /**
     * 逻辑Binlog的保存目录
     */
    public static final String BINLOG_DIR_PATH = "binlog.dir.path";
    /**
     * 单个逻辑Binlog文件的大小,单位：字节
     */
    public static final String BINLOG_FILE_SIZE = "binlog.file.size";
    /**
     * 对逻辑Binlog进行seek操作时的缓冲区大小，单位：M
     */
    public static final String BINLOG_FILE_SEEK_BUFFER_SIZE = "binlog.file.seek.bufferSize";
    /**
     * 写binlog文件时是否进入dryrun模式
     */
    public static final String BINLOG_WRITE_DRYRUN = "binlog.write.dryrun";
    /**
     * 写binlog文件时是否支持记录RowQueryLogEvent
     */
    public static final String BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG = "binlog.write.supportRowsQueryLog";
    /**
     * 逻辑binlog写缓冲区的大小，单位字节，建议为2的倍数
     */
    public static final String BINLOG_WRITE_BUFFER_SIZE = "binlog.write.buffer.size";
    /**
     * 逻辑Binlog Write Buffer中的数据flush的策略，0-每个事务flush一次，1-定时flush
     */
    public static final String BINLOG_WRITE_FLUSH_POLICY = "binlog.write.flush.policy";
    /**
     * 逻辑Binlog Write Buffer的flush的间隔（单位：毫秒），当flush策略为1时有效
     */
    public static final String BINLOG_WRITE_FLUSH_INTERVAL = "binlog.write.flush.interval";
    /**
     * 心跳刷盘频率，默认30秒
     */
    public static final String HEARTBEAT_FLUSH_INTERVAL = "binlog.write.heartbeatFlushInterval";
    /**
     * tableId的初始值
     */
    public static final String BINLOG_WRITE_TABLE_ID_BASE_VALUE = "binlog.write.tableId.baseValue";
    //******************************************************************************************************************
    //***************************************************Task运行时相关的配置**********************************************
    //******************************************************************************************************************
    /**
     * 在task进行bootstrap的过程中，是否自动启动task engine内核，默认为false
     */
    public static final String TASK_ENGINE_AUTO_START = "task.engine.autoStart";
    /**
     * extractor是否记录每个事务的信息，用于数据审计，默认false
     */
    public static final String TASK_EXTRACTOR_RECORD_TRANSLOG = "task.extractor.recordTransLog";
    /**
     * merger是否开启dry run，开启的话则不会向collector发送数据
     */
    public static final String TASK_MERGER_DRYRUN = "task.merger.dryRun";
    /**
     * merger所处的dry-run模式，0-before merge barrier，1-after merge barrier
     */
    public static final String TASK_MERGER_DRYRUN_MODE = "task.merger.dryRun.mode";
    /**
     * merger是否合并事务策略不是Tso的Xa事务，目前只能设置为false
     */
    public static final String TASK_MERGER_MERGE_NOTSO_XA = "task.merger.mergeNoTsoXa";
    /**
     * 心跳窗口被强制force complete的阈值
     */
    public static final String TASK_HB_WINDOW_FORCE_COMPLETE_THRESHOLD = "task.hbwindow.forceComplete.threshold";
    /**
     * transmitter发送数据时的组包模式，ITEMSIZE or MEMSIZE
     */
    public static final String TASK_TRANSMITTER_CHUNK_MODE = "task.transmitter.chunkMode";
    /**
     * transmitter发送数据包时，每个Chunk的ItemSize
     */
    public static final String TASK_TRANSMITTER_CHUNK_ITEMSIZE = "task.transmitter.chunkItemSize";
    /**
     * 每个DumpReply包含的TxnMessage的最大大小，默认值为1G，单位：Byte
     */
    public static final String TASK_TRANSMITTER_MAX_MESSAGE_SIZE = "task.transmitter.maxMessageSize";
    /**
     * transmitter是否开启dry run，默认false
     */
    public static final String TASK_TRANSMITTER_DRYRUN = "task.transmitter.dryRun";
    /**
     * 是否支持binlog中显示隐藏主键
     */
    public static final String TASK_DRDS_HIDDEN_PK_SUPPORT = "task.support.drdsHiddenPk";
    /**
     * 是否忽略TraceId乱序，默认false
     */
    public static final String TASK_TRACEID_DISORDER_IGNORE = "task.traceid.disorder.ignore";

    /**
     * 忽略buffer key重复异常
     */
    public static final String TASK_EXCEPTION_SKIP_DUPLICATE_BUFFER_KEY = "task.skip.exception.bufferKey.duplicate";

    //******************************************************************************************************************
    //***********************************************Daemon和调度相关参数*************************************************
    //******************************************************************************************************************

    /**
     * Daemon进程执行心跳的间隔
     */
    public static final String DAEMON_HEARTBEAT_INTERVAL_MS = "daemon.heartbeat.interval.ms";
    /**
     * Damone Leader进程监测是否需要重新构建拓扑的时间隔
     */
    public static final String DAEMON_TOPOLOGY_WATCH_INTERVAL_MS = "daemon.topology.watch.interval.ms";
    /**
     * Daemon监控Task或Dumper是否健康的时间间隔
     */
    public static final String DAEMON_TASK_WATCH_INTERVAL_MS = "daemon.task.watch.interval.ms";
    /**
     * Daemon判读Task或Dumper是否已经心跳超时的阈值
     */
    public static final String DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS = "daemon.task.watch.heartbeat.timeout.ms";
    /**
     * Daemon leader节点执行TSO心跳的时间间隔
     */
    public static final String DAEMON_TSO_HEARTBEAT_INTERVAL = "daemon.tso.heartbeat.interval.ms";
    /**
     * 进行拓扑构建时，对节点最小数量的要求
     */
    public static final String TOPOLOGY_NODE_MINSIZE = "topology.node.minsize";
    /**
     * 进行拓扑构建时，触发Relay类型Task的阈值，目前暂时不支持，值设置的很大
     */
    public static final String TOPOLOGY_STORAGE_TRIGGER_RELAY_THRESHOLD = "topology.storage.triggerRelay.threshold";
    /**
     * 触发重新进行拓扑构建的心跳超时阈值，心跳指的是Daemon的心跳
     */
    public static final String TOPOLOGY_HEARTBEAT_TIMEOUT_MS = "topology.heartbeat.timeout.ms";
    /**
     * 重建构建拓扑后，等待task或dumper启动成功的超时时间，未发生超时前不会进行下一轮的拓扑重建
     */
    public static final String TOPOLOGY_START_TIMEOUT_MS = "topology.waitStart.timeout.ms";
    /**
     * 拓扑中的组件Dumper&Task，执行心跳的时间间隔
     */
    public static final String TOPOLOGY_TASK_HEARTBEAT_INTERVAL = "topology.task.heartbeat.interval.ms";
    /**
     * 构建拓扑时，可以使用该机器上内存资源的比率
     */
    public static final String TOPOLOGY_RESOURCE_USE_RATIO = "topology.resource.useRatio";
    /**
     * 分配资源时，分配给Dumper的比重
     */
    public static final String TOPOLOGY_RESOURCE_DUMPER_WEIGHT = "topology.resource.dumper.weight";
    /**
     * 分配资源时，分配给Task的比重
     */
    public static final String TOPOLOGY_RESOURCE_TASK_WEIGHT = "topology.resource.task.weight";

    //******************************************************************************************************************
    //***********************************************报警 &报警事件相关参数*************************************************
    //******************************************************************************************************************

    /**
     * 是否开启CDC内核自带的报警能力
     */
    public static final String ALARM_OPEN = "alarm.open";
    /**
     * 接收报警的用户ID，多个ID逗号分隔
     */
    public static final String ALARM_EMPIDS = "alarm.empIds";
    /**
     * dumper或task长时间未收到数据，触发报警的时间阈值
     */
    public static final String ALARM_NODATA_THRESHOLD = "alarm.nodata.threshold";
    /**
     * dumper触发延迟报警的阈值，单位：S
     */
    public static final String ALARM_DELAY_THRESHOLD = "alarm.delay.threshold";
    /**
     * 是否上报异常事件
     */
    public static final String ALARM_REPORT_ALARM_EVENT = "alarm.reportAlarmEvent";

    //******************************************************************************************************************
    //***********************************************KV 存储相关参数******************************************************
    //******************************************************************************************************************

    /**
     * 持久化功能是否开启，默认开启
     */
    public static final String STORAGE_IS_PERSIST_ON = "storage.isPersistOn";
    /**
     * 是否强制走磁盘模式
     */
    public static final String STORAGE_FORCE_PERSIST = "storage.forcePersist";
    /**
     * 磁盘存储目录
     */
    public static final String STORAGE_PERSIST_PATH = "storage.persistBasePath";
    /**
     * 触发磁盘存储的内存阈值
     */
    public static final String STORAGE_PERSIST_MEMORY_THRESHOLD = "storage.persistMemoryThreshold";
    /**
     * 单个事务触发磁盘存储的阈值
     */
    public static final String STORAGE_TXN_PERSIST_THRESHOLD = "storage.txnPersistThreshold";
    /**
     * 单个Event触发磁盘存储的阈值
     */
    public static final String STORAGE_TXNITEM_PERSIST_THRESHOLDE = "storage.txnItemPersistThreshold";
    /**
     * 磁盘中数据的删除模式
     */
    public static final String STORAGE_PERSIST_DELETE_MODE = "storage.persistDeleteMode";
    /**
     * 磁盘存储单元的个数
     */
    public static final String STORAGE_PERSIST_REPO_UNIT_COUNT = "storage.persistRepoUnitCount";
    /**
     * 清理存储资源的线程个数
     */
    public static final String STORAGE_CLEAN_WORKER_COUNT = "storage.cleanWorker.count";

    //******************************************************************************************************************
    //***********************************************Polarx库表元数据相关参数**********************************************
    //******************************************************************************************************************

    /**
     * 是否开启修复拓扑元数据开关，修复数据场景使用
     */
    public static final String META_OPEN_TOPOLOGY_REPAIR = "meta.openTopologyRepair";
    /**
     * 是否优先使用HistoryTable中的记录构建Repository，修复数据场景使用
     */
    public static final String META_USE_HISTORY_TABLE_FIRST = "meta.useHistoryTableFirst";
    /**
     * Rollback时，元数据的构建方式
     */
    public static final String META_ROLLBACK_MODE = "meta.rollback.mode";
    /**
     * 查询不到物理表元数据时，支持instant create的rollback mode
     */
    public static final String META_ROLLBACK_MODE_SUPPORT_INSTANT_CREATE_TABLE =
        "meta.rollback.mode.supportInstantCreatTable";
    /**
     * 每次执行完逻辑DDL后，是否对该DDL对应的表的元数据信息进行一致性检查
     */
    public static final String META_CHECK_CONSISTENCY_AFTER_EACH_APPLY = "meta.checkConsistencyAfterEachApply";
    /**
     * 每次执行完apply后是否打印当前表的汇总信息
     */
    public static final String META_PRINT_SUMMARY_AFTER_APPLY_SWITCH = "meta.printSummaryAfterApply.switch";
    /**
     * 每次执行完apply后，需要打印表汇总信息的表清单，支持正则表达式
     */
    public static final String META_PRINT_SUMMARY_AFTER_APPLY_TABLES = "meta.printSummaryAfterApply.tables";
    /**
     * 每次执行完apply后是否打印当前表的详细信息
     */
    public static final String META_PRINT_DETAIL_AFTER_APPLY_SWITCH = "meta.printDetailAfterApply.switch";
    /**
     * 每次执行完apply后，需要打印表汇总信息的表清单，支持正则表达式
     */
    public static final String META_PRINT_DETAIL_AFTER_APPLY_TABLES = "meta.printDetailAfterApply.tables";
    /**
     * SemiSnapshot的保存时间，超过该时间的snapshot会被移除，单位：小时
     */
    public static final String META_SEMI_SNAPSHOT_HOLDING_TIME = "meta.semiSnapshot.holdingTime";
    /**
     * 检查SemiSnapshot的holdingtime的时间间隔，单位：分钟
     */
    public static final String META_SEMI_SNAPSHOT_HOLDING_TIME_CHECK_INTERVAL =
        "meta.semiSnapshot.holdingTime.checkInterval";
    /**
     * 对deltaChangeMap中全量进行一致性检测的周期，单位：秒
     */
    public static final String META_SEMI_SNAPSHOT_DELTA_CHANGE_CHECK_INTERVAL =
        "meta.semiSnapshot.deltaChange.checkInterval";

    //******************************************************************************************************************
    //*********************************Binlog_System_Config表中有，但config文件中没有的一些配置******************************
    //******************************************************************************************************************

    public static final String EXPECTED_STORAGE_TSO_KEY =
        String.format("%s:expected_storage_tso",
            SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_SNAPSHOT_VERSION_KEY =
        String.format("%s:cluster_snapshot_version",
            SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY =
        String.format("%s:cluster_topology_exclude_nodes",
            SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY =
        String.format("%s:cluster_topology_dumper_master_node",
            SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_SUSPEND_TOPOLOGY_REBUILDING =
        String.format("%s:cluster_suspend_topology_rebuilding",
            SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID));
}
