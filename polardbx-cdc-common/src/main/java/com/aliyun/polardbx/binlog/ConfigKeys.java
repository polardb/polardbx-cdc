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
     * 当前CDC实例所属用户的的uid
     */
    public static final String RDS_UID = "rdsUid";
    /**
     * 当前CDC实例所属用户的bid
     */
    public static final String RDS_BID = "rdsBid";
    /**
     * RDS API 网关地址
     */
    public static final String RDS_API_URL = "rds_api_url";
    /**
     * RDS API access id
     */
    public static final String RDS_API_ACCESS_ID = "rds_api_access_id";
    /**
     * RDS API access key
     */
    public static final String RDS_API_ACCESS_KEY = "rds_api_access_key";
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
    /**
     * 检测连接是否可用的超时时间
     */
    public static final String DATASOURCE_WRAPPER_CHECK_VALID_TIMEOUT_SEC = "datasource.wrapper.checkValid.timeout.sec";

    //******************************************************************************************************************
    //********************************************Rpc Protocol协议交互相关配置**************************************
    //******************************************************************************************************************
    /**
     * TxnStream，对dump reply进行封包的方式，默认：BYTES，可以进行两阶段序列化，有更好的性能
     */
    public static final String BINLOG_TXN_STREAM_DUMP_REPLY_PACKET_MODE = "binlog.txn.stream.dumpReply.packetMode";
    /**
     * TxnStream，是否使用异步模式，默认true，异步模式下序列化线程和binlog write线程各自独立，可以带来更有的性能
     */
    public static final String BINLOG_TXN_STREAM_CLIENT_USE_ASYNC_MODE = "binlog.txn.stream.client.useAsyncMode";
    /**
     * TxnStream，使用异步模式时，接收队列的大小，默认256
     */
    public static final String BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE =
        "binlog.txn.stream.client.receive.queue.size";
    /**
     * TxnStream，Client端flow control window的大小
     */
    public static final String BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE =
        "binlog.txn.stream.flowControl.window.size";

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
     * 写binlog文件时是否开启dry run
     */
    public static final String BINLOG_WRITE_DRYRUN = "binlog.write.dryRun";
    /**
     * dry run mode，0 - on receive，1 - on push，2 - on sink
     */
    public static final String BINLOG_WRITE_DRYRUN_MODE = "binlog.write.dryRun.mode";
    /**
     * 写binlog文件时是否支持记录RowQueryLogEvent
     */
    public static final String BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG = "binlog.write.supportRowsQueryLog";
    /**
     * 逻辑binlog写缓冲区的大小，单位字节，建议为2的倍数
     */
    public static final String BINLOG_WRITE_BUFFER_SIZE = "binlog.write.buffer.size";
    /**
     * 逻辑Binlog写入时，缓冲区是否使用直接内存，默认true
     */
    public static final String BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER = "binlog.write.useDirectByteBuffer";
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
    /**
     * 是否开启流水线多线程并行写入，默认true
     */
    public static final String BINLOG_WRITE_USE_PARALLEL = "binlog.write.useParallel";
    /**
     * 开启并行写入时的并行度，默认2
     */
    public static final String BINLOG_WRITE_PARALLELISM = "binlog.write.parallelism";
    /**
     * 开启并行写入时，是否使用batch模式，即是否使用BatchEventToken，默认：true
     * batch模式主要用来解决每个binlog event很小但量很大时的性能问题，非batch模式下，每个binlog event会占据ringbuffer的一个槽位，会导致
     * 生产者和消费之间过于频繁的线程上下文切换，通过使用batch模式，每个槽位对应的是一批binlog event，从而提升吞吐
     */
    public static final String BINLOG_WRITE_PARALLEL_USE_BATCH = "binlog.write.parallel.useBatch";
    /**
     * 开启并行写入时，RingBuffer缓冲区的大小，默认65536
     */
    public static final String BINLOG_WRITE_PARALLEL_BUFFER_SIZE = "binlog.write.parallel.buffer.size";
    /**
     * 开启并行写入时，RingBuffer中每个event data 缓冲区的大小，默认1024 byte
     */
    public static final String BINLOG_WRITE_PARALLEL_EVENT_DATA_BUFFER_SIZE =
        "binlog.write.parallel.eventData.buffer.size";
    /**
     * 开启并行写入时，RingBuffer中每个event data 所能持有的最大载荷，默认65536 byte，和BINLOG_WRITE_PARALLEL_BUFFER_SIZE一起共同决定了
     * RingBuffer缓冲区占满时，所需要的最大内存空间
     */
    public static final String BINLOG_WRITE_PARALLEL_EVENT_DATA_MAX_SIZE = "binlog.write.parallel.eventData.maxSize";
    /**
     * 读取binlog文件的缓冲区大小，单位字节，默认：10485760
     */
    public static final String BINLOG_SYNC_READ_BUFFER_SIZE = "binlog.sync.read.buffer.size";
    /**
     * dumper主备复制单个packet的最大大小，单位：字节
     */
    public static final String BINLOG_SYNC_PACKET_SIZE = "binlog.sync.packet.size";
    /**
     * dumper主备复制，客户端是否使用异步模式，默认：true，异步模式下packet反序列化和文件写入操作分属不同线程，有更好的性能表现
     */
    public static final String BINLOG_SYNC_CLIENT_USE_ASYNC_MODE = "binlog.sync.client.useAsyncMode";
    /**
     * dumper主备复制，客户端接收队列的大小，默认：64，异步模式下有效
     */
    public static final String BINLOG_SYNC_CLIENT_RECEIVE_QUEUE_SIZE = "binlog.sync.client.receive.queue.size";
    /**
     * dumper主备复制，客户端flow control window的大小，单位：M，默认800M
     */
    public static final String BINLOG_SYNC_FLOW_CONTROL_WINDOW_SIZE =
        "binlog.sync.flowControl.window.size";
    /**
     * dumper主备复制，对event进行拆包的方式，默认client端拆包，可以有更好的性能
     */
    public static final String BINLOG_SYNC_EVENT_SPLIT_MODE = "binlog.sync.event.split.mode";

    public static final String BINLOG_SYNC_INJECT_TROUBLE = "binlog.sync.injectTrouble";
    /**
     * dumper对下游消费订阅，进行数据推送单个packet的最大大小，单位：字节
     */
    public static final String BINLOG_DUMP_PACKET_SIZE = "binlog.dump.packet.size";
    /**
     * dumper对下游消费订阅，从binlog文件进行数据读取缓冲区的大小，单位：字节
     */
    public static final String BINLOG_DUMP_READ_BUFFER_SIZE = "binlog.dump.read.buffer.size";
    /**
     * 逻辑Binlog文件的备份方式，OSS or Lindorm or NULL
     */
    public static final String BINLOG_BACKUP_TYPE = "binlog.backup.type";
    /**
     * 逻辑Binlog文件在OSS 或 Lindorm 的过期时间，单位：天
     */
    public static final String BINLOG_BACKUP_EXPIRE_DAYS = "binlog.backup.expire.days";
    /**
     * 逻辑Binlog文件进行备份上传时，缓冲区的大小
     */
    public static final String BINLOG_BACKUP_UPLOAD_BUFFER_SIZE = "binlog.backup.upload.bufferSize";
    /**
     * 逻辑Binlog文件备份上传的最大执行线程数
     */
    public static final String BINLOG_BACKUP_UPLOAD_MAX_THREAD_NUM = "binlog.backup.upload.maxThreadNum";
    /**
     * 从OSS下载Binlog文件的线程数
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_PARALLELISM = "binlog.backup.download.parallelism";
    /**
     * 从备份存储下载Binlog文件到本地，可以占用CDC最大磁盘量的比例
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_SIZE_RATIO = "binlog.backup.download.size.ratio";
    /**
     * 等待文件上传成功的时间阈值，单位:s
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_WAIT_UPLOAD_TIMEOUT = "binlog.backup.download.waitUpload.timeout";
    /**
     * 当待下载的文件列表，有部分文件未上传成功，且已经等待超时，是否采取Best Effort模式，尽量下载，默认：true
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_BEST_EFFORT_WITH_GAP = "binlog.backup.download.bestEffortWithGap";
    /**
     * 上传Binlog文件到OSS或Lindorm的模式，默认为APPEND，Lindorm不支持Mutiple
     */
    public static final String BINLOG_BACKUP_UPLOAD_MODE = "binlog.backup.uploadMode";
    /**
     * Binlog文件上传到备份存储的模式为APPEND时，最大可Append的FileSize，超过该Size将转化为Multiple模式，如果支持Multiple的话，单位：G
     */
    public static final String BINLOG_BACKUP_UPLOAD_MAX_APPEND_FILE_SIZE = "binlog.backup.upload.maxAppendFileSize";
    /**
     * Binlog文件上传到备份存储的模式为APPEND时，最大可Append的FileSize，超过该Size将转化为Multiple模式，如果支持Multiple的话，单位：字节
     */
    public static final String BINLOG_BACKUP_UPLOAD_PART_SIZE = "binlog.backup.upload.partSize";
    /**
     * Binlog文件上传到备份存储，从本地文件fetch data时的超时时间，单位：ms
     */
    public static final String BINLOG_BACKUP_UPLOAD_WAIT_DATA_TIMEOUT_MS = "binlog.backup.upload.waitData.timeout.ms";
    /**
     * CDC 逻辑binlog下载链接有效时长，单位：秒
     */
    public static final String BINLOG_DOWNLOAD_LINK_AVAILABLE_INTERVAL = "binlog.downloadLink.available.interval";
    /**
     * 过期逻辑Binlog文件，检测间隔，单位分钟
     */
    public static final String BINLOG_CLEANER_CHECK_INTERVAL = "binlog.cleaner.check.interval";
    /**
     * 触发本地清理的阈值，默认90%
     */
    public static final String BINLOG_CLEANER_CLEAN_THRESHOLD = "binlog.cleaner.clean.threshold";
    /**
     * 是否支持对本地binlog进行清理
     */
    public static final String BINLOG_CLEANER_CLEAN_ENABLE = "binlog.cleaner.clean.enable";
    /**
     * 保存下载文件目录
     */
    public static final String BINLOG_DOWNLOAD_DATA_FILE_DIR = "binlog.download.data.dir";
    /**
     * 通过该配置，可以对binlog文件中的last tso进行overwrite，格式 [old tso, overwritten tso]
     */
    public static final String BINLOG_RECOVERY_START_TSO_OVERWRITE_CONFIG = "binlog.recovery.startTso.overwrite.config";
    /**
     * 逻辑Binlog文件上传到OSS的accessKeyId
     */
    public static final String OSS_ACCESSKEY_ID = "oss.accessKeyId";
    /**
     * 逻辑Binlog文件上传到OSS的accessKeySecret
     */
    public static final String OSS_ACCESSKEY_ID_SECRET = "oss.accessKeySecret";
    /**
     * 逻辑Binlog文件上传到OSS的bucket
     */
    public static final String OSS_BUCKET = "oss.bucket.name";
    /**
     * 逻辑Binlog文件上传到OSS的endpoint
     */
    public static final String OSS_ENDPOINT = "oss.endpoint";
    /**
     * 逻辑Binlog文件上传到Lindorm的accessKeyId
     */
    public static final String LINDORM_ACCESSKEY_ID = "lindorm.accessKeyId";
    /**
     * 逻辑Binlog文件上传到Lindorm的accessKeySecret
     */
    public static final String LINDORM_ACCESSKEY_ID_SECRET = "lindorm.accessKeySecret";
    /**
     * 逻辑Binlog文件上传到Lindorm的bucket
     */
    public static final String LINDORM_BUCKET = "lindorm.bucket.name";
    /**
     * 逻辑Binlog文件上传到Lindorm的endpoint
     */
    public static final String LINDORM_ENDPOINT = "lindorm.endpoint";
    /**
     * 逻辑Binlog文件上传到Lindorm的端口
     */
    public static final String LINDORM_PORT = "lindorm.port";
    /**
     * 从Lindorm下载逻辑文件的端口
     */
    public static final String LINDORM_DOWNLOAD_PORT = "lindorm.downloadPort";

    //******************************************************************************************************************
    //***************************************************Task运行时相关的配置**********************************************
    //******************************************************************************************************************

    /**
     * 在task进行bootstrap的过程中，是否自动启动task engine内核，默认为false
     */
    public static final String TASK_ENGINE_AUTO_START = "task.engine.autoStart";
    /**
     * extractor是否记录每个事务的信息，用于数据审计，默认true
     */
    public static final String TASK_EXTRACTOR_RECORD_TRANSLOG = "task.extractor.recordTransLog";
    /**
     * 逻辑表黑名单，黑名单中的表会被过滤掉，格式：dbname1.tbname1,dbname1.tbname2,dbname2.tbname1,...
     */
    public static final String TASK_EXTRACTOR_LOGIC_TABLE_BLACKLIST = "task.extractor.logicTable.blacklist";
    /**
     * 逻辑库黑名单，黑名单中的库会被过滤掉，格式: dbname1,dbname2,dbname3,...
     */
    public static final String TASK_EXTRACTOR_LOGIC_DB_BLACKLIST = "task.extractor.logicDb.blacklist";
    /**
     * 是否强制从backup存储下载rds binlog并消费
     */
    public static final String TASK_RDSBINLOG_FORCE_CONSUME_BACKUP = "task.rdsbinlog.forceConsumeBackup";
    /**
     * rds binlog下载到本地的目录
     */
    public static final String TASK_RDSBINLOG_DOWNLOAD_DIR = "task.rdsbinlog.download.dir";
    /**
     * 从oss下载rds binlog时，下载最近多少天的binlog文件 RDS_PREFER_HOST_MAP
     */
    public static final String TASK_RDSBINLOG_DOWNLOAD_RECALLDAYS = "task.rdsbinlog.download.recall.days";
    /**
     * 下载Binlog文件时，指定所属的host
     */
    public static final String TASK_RDSBINLOG_DOWNLOAD_ASSINGED_HOST = "task.rdsbinlog.download.assigned.host";
    /**
     * 下载Binlog文件时，指定所属的host
     */
    public static final String TASK_RDSBINLOG_DOWNLOAD_NUM = "task.rdsbinlog.download.num";
    /**
     * 下载Binlog文件时，所能使用的磁盘限制
     */
    public static final String TASK_RDSBINLOG_DISK_LIMIT = "task.rdsbinlog.disk.limit";

    /**
     * 下载Binlog文件时，单个DN所能使用的磁盘限制
     */
    public static final String TASK_RDSBINLOG_STORAGE_DISK_LIMIT = "task.rdsbinlog.storage.disk.limit";
    /**
     * 下载Binlog文件时，所能使用的线程限制
     */
    public static final String TASK_RDSBINLOG_THREAD_LIMIT = "task.rdsbinlog.thread.limit";
    /**
     * merger是否开启dry run，开启的话则不会向collector发送数据
     */
    public static final String TASK_MERGER_DRYRUN = "task.merger.dryRun";
    /**
     * merger所处的dry-run模式:
     * 0- not push token to merge source
     * 1- before merge barrier
     * 2- after merge barrier
     */
    public static final String TASK_MERGER_DRYRUN_MODE = "task.merger.dryRun.mode";
    /**
     * merger是否合并事务策略不是Tso的Xa事务，目前只能设置为false
     */
    public static final String TASK_MERGER_MERGE_NOTSO_XA = "task.merger.mergeNoTsoXa";
    /**
     * 事务归并排序的方式，SINGLE or BATCH
     */
    public static final String TASK_MERGER_MERGE_TYPE = "task.merger.mergeType";
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
     * 每个DumpReply包含的TxnMessage的最大大小，默认值为100M，单位：Byte
     */
    public static final String TASK_TRANSMITTER_MAX_MESSAGE_SIZE = "task.transmitter.maxMessageSize";
    /**
     * collector merge阶段的并行度
     */
    public static final String TASK_COLLECTOR_MERGE_STAGE_PARALLELISM = "task.collector.mergeStage.parallelism";
    /**
     * 在collector阶段构建DumpReply Packet的阈值，默认65536(单位：字节)，TxnBuffer size小于该阈值时，会在collector阶段构建packet，以提升性能
     */
    public static final String TASK_COLLECTOR_BUILD_PACKET_THRESHOLD = "task.collector.buildPacket.threshold";
    /**
     * transmitter是否开启dry run，默认false
     */
    public static final String TASK_TRANSMITTER_DRYRUN = "task.transmitter.dryRun";
    /**
     * transmitter dry run mode, 0 - before dumping queue ,1 - before send to dumper
     */
    public static final String TASK_TRANSMITTER_DRYRUN_MODE = "task.transmitter.dryRun.mode";
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
    /**
     * 对事务进行跳过的白名单，多个事务用#分隔
     */
    public static final String TASK_TRANSACTION_SKIP_WHITELIST = "task.transaction.skip.whitelist";
    /**
     * 事务白名单动态跳过堆积检测阈值
     */
    public static final String TASK_TRANSACTION_SKIP_THRESHOLD = "task.transaction.skip.threshold";
    /**
     * 随机丢失commit信息开关，只用在实验室环境
     */
    public static final String TASK_TRANSACTION_RANDOM_DISCARD_COMMIT = "task.transaction.random.discard.commit";
    /**
     * merge source queue的大小，默认1024
     */
    public static final String TASK_QUEUE_MERGESOURCE_SIZE = "task.queue.mergeSource.size";
    /**
     * 所有merge source queue的size求和之后的最大限制值，防止过多导致内存吃紧，比如对接1024个DN的场景，如果每个mergesource的queue size还是1024
     */
    public static final String TASK_QUEUE_MERGESOURCE_MAX_TOTAL_SIZE = "task.queue.mergeSource.maxTotalSize";
    /**
     * collector queue的大小
     */
    public static final String TASK_QUEUE_COLLECTOR_SIZE = "task.queue.collector.size";
    /**
     * transmitter queue的大小
     */
    public static final String TASK_QUEUE_TRANSMITTER_SIZE = "task.queue.transmitter.size";
    public static final String TASK_QUEUE_DUMPING_SIZE = "task.queue.dumping.size";
    /**
     * search tso时，是否必须要求search到的rollback tso必须大于等于上一次ScaleOut或ScaleIn的Tso，默认为false
     * 主要用在测试环境，同一个storage被反复add和remove时，需保证rollback tso大于等于最近一次StorageChange的tso，否则构建出来的元数据有问题
     * 未经过充分完整测试，生产环境慎用
     */
    public static final String TASK_SEARCH_TSO_CHECK_PRE_STORAGE_CHANGE = "task.searchTso.isCheckPreStorageChange";

    /**
     * 当RDS或者源Mysql ddl中包含hints时，如果解析报错，可以通过次开关remove掉hints
     */
    public static final String TASK_DDL_REMOVEHINTS_SUPPORT = "task.ddl.removeHints";

    /**
     * oss快速搜索模式， 正常搜索倒序每个文件都搜一把，快速模式忽略跨文件事务
     */
    public static final String TASK_SEARCHTSO_QUICKMODE = "task.searchTso.quickMode";

    /**
     * 打开 event 处理log
     */
    public static final String TASK_EVENT_COMMITLOG = "task.event.commitLog";

    /**
     * 采用老版本下载模式
     */
    public static final String TASK_SEARCHTSO_OLDMODE = "task.searchTso.oldMode";

    /**
     * 搜索位点使用v1算法
     */
    public static final String TASK_SEARCHTSO_HANDLE_V1 = "task.searchTso.handle.v1";
    /**
     * oss 下载cache大小
     */
    public static final String TASK_OSS_CACHE_SIZE = "task.oss.cache.size";
    /**
     * oss 模式
     */
    public static final String TASK_OSS_CACHE_MODE = "task.oss.cache.mode";
    /**
     * oss 内存最大使用量
     */
    public static final String TASK_OSS_CACHE_MAXSIZE = "task.oss.cache.maxsize";

    /**
     * 列 类型整形支持
     */
    public static final String TASK_EXTRACTOR_ROWIMAGE_TYPE_REBUILD_SUPPORT =
        "task.extractor.rowImage.type.rebuild.support";

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
     * 对非本机的task进行
     */
    public static final String DAEMON_TASK_STOP_NOLOCAL_WHITLIST = "daemon.task.stop.nolocal.whitelist";
    /**
     * Daemon leader节点执行TSO心跳的时间间隔
     */
    public static final String DAEMON_TSO_HEARTBEAT_INTERVAL = "daemon.tso.heartbeat.interval.ms";
    /**
     * 用来配置TSO心跳间隔是否支持自适应模式，默认：true
     * 自适应模式主要用来提升性能，在大部分事务都是单机事务时，eps的性能瓶颈会受限于tso心跳的频率
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_ENABLE =
        "daemon.tso.heartbeat.selfAdaption.enable";
    /**
     * TSO心跳间隔开启自适应模式时，心跳频率自动调整的目标值，默认：10ms，设定的值要比默认值小
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL =
        "daemon.tso.heartbeat.selfAdaption.targetInterval";
    /**
     * TSO心跳间隔开启自适应模式时，触发自动调整的EPS阈值，该阈值之下保持默认心跳间隔，该阈值之上调整心跳间隔为目标值
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_THRESHOLD_EPS =
        "daemon.tso.heartbeat.selfAdaption.threshold.eps";
    /**
     * 对ddl相关维度的数据进行监控检测的周期频率
     */
    public static final String DAEMON_HISTORY_CHECK_INTERVAL_MINUTES = "daemon.history.check.interval.minute";
    /**
     * 是否支持在binlog集群，运行Replica
     */
    public static final String DAEMON_CLUSTER_BINLOG_SUPPORT_RUN_REPLICA = "daemon.cluster.binlog.supportRunReplica";
    /**
     * 进行拓扑构建时，对节点最小数量的要求
     */
    public static final String TOPOLOGY_NODE_MINSIZE = "topology.node.minsize";
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
     * Dumper slave可以使用的最大内存，默认8G，让出更多内存给Task
     */
    public static final String TOPOLOGY_RESOURCE_DUMPER_SLAVE_MAX_MEM = "topology.resource.dumper.slave.maxMem";
    /**
     * 分配资源时，分配给Task的比重
     */
    public static final String TOPOLOGY_RESOURCE_TASK_WEIGHT = "topology.resource.task.weight";
    /**
     * 兼容性开关，收到scaleout或者scalein打标信息时，是否对storage list进行修复
     */
    public static final String TOPOLOGY_SCALE_REPAIR_STORAGE_ENABLE = "topology.scale.repairStorage.enable";

    //******************************************************************************************************************
    //***********************************************报警 &报警事件相关参数*************************************************
    //******************************************************************************************************************
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
    public static final String STORAGE_PERSIST_MODE = "storage.persist.mode";
    /**
     * 磁盘存储目录
     */
    public static final String STORAGE_PERSIST_PATH = "storage.persistBasePath";
    /**
     * 通过检测内存阈值决定是否需要触发落盘的频率，单位：ms，默认5s一次
     */
    public static final String STORAGE_PERSIST_CHECK_INTERVAL_MILLS = "storage.persistCheckInterval.mills";
    /**
     * 对存量TxnBuffer进行磁盘转储的阈值，默认阈值95%(0.95)
     */
    public static final String STORAGE_PERSIST_ALL_THRESHOLD = "storage.persistAllThreshold";
    /**
     * 新创建的TxnBuffer，触发磁盘存储的内存阈值，默认阈值为85%(0.85)
     * <p>
     * 该参数的设定值应该大于等于-XX:CMSInitiatingOccupancyFraction的设定值，如果小于会导致过早触发落盘，而后内存一直不再显著增长，
     * 一直使用磁盘模式会对性能有一定影响。作为流式系统，大多数对象具有朝生夕灭的特点，如果老年代内存使用率超过了CMSInitiatingOccupancyFraction
     * 的设定值则会触发GC，正常情况GC后应该会有大量内存被释放，水位降低后自然不会触发落盘操作。但也有可能GC后，内存没有丝毫下降，此时则需要开始
     * 注意OutOfMemory的风险，如果再继续网上涨，则触发落盘操作。什么情况会导致内存一直往上涨？比如：
     * 1.发生了持续很长时间的事务空洞，导致大量事务发生了block，@see com.aliyun.polardbx.binlog.extractor.sort.Sorter
     * 2.多路归并，有一路或者多路有问题，长时间没有数据，也会导致大量事务block， @see com.aliyun.polardbx.binlog.merge.LogEventMerger
     * 3.Dumper消费慢，导致整个链路block了大量事务
     */
    public static final String STORAGE_PERSIST_NEW_THRESHOLD = "storage.persistNewThreshold";
    /**
     * 单个事务触发磁盘存储的阈值，超过指定的阈值，触发落盘，单位：字节
     */
    public static final String STORAGE_TXN_PERSIST_THRESHOLD = "storage.txnPersistThreshold";
    /**
     * 单个Event触发磁盘存储的阈值，超过指定的阈值，触发落盘，单位：字节
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
    /**
     * 对数据进行restore时是否支持并行操作，默认true
     */
    public static final String STORAGE_PARALLEL_RESTORE_ENABLE = "storage.parallelRestore.enable";
    /**
     * 进行并行restore时的并发度，默认4
     */
    public static final String STORAGE_PARALLEL_RESTORE_PARALLELISM = "storage.parallelRestore.parallelism";
    /**
     * 进行并行restore时，每个批次的TxnItem条数，默认200，不能太大，否则有内存溢出风险
     */
    public static final String STORAGE_PARALLEL_RESTORE_BATCH_SIZE = "storage.parallelRestore.batchSize";
    /**
     * 进行并行restore时，eventSize的最大值，如果大于该阈值，则不进行restore操作
     */
    public static final String STORAGE_PARALLEL_RESTORE_MAX_EVENT_SIZE = "storage.parallelRestore.maxEventSize";

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
     * check fastsql校验结果，实验室中打开
     */
    public static final String META_CHECK_FASTSQL_FORMAT_RESULT = "meta.checkFastsqlFormatResult";
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
     * 是否支持semi snapshot，默认为true，注：即使设置为false，rollback mode也是可以设置为SNAPSHOT_SEMI的，找不到semi记录会自动切换为SNAPSHOT_EXACTLY
     * 即：该参数和rollback mode没有约束关系，rollback mode即使没有使用SNAPSHOT_SEMI，仍然可以对semi_snapshot进行更新维护
     */
    public static final String META_SEMI_SNAPSHOT_ENABLE = "meta.semiSnapshot.enable";
    /**
     * SemiSnapshot的保存时间，超过该时间的snapshot会被移除，单位：小时
     */
    public static final String META_SEMI_SNAPSHOT_HOLDING_TIME = "meta.semiSnapshot.holdingTime";
    /**
     * 对deltaChangeMap中全量进行一致性检测的周期，单位：秒
     */
    public static final String META_SEMI_SNAPSHOT_DELTA_CHANGE_CHECK_INTERVAL =
        "meta.semiSnapshot.deltaChange.checkInterval";
    /**
     * 当未发生实际schema变更时，是否记录create if not exists 或者 drop if exists 到 binlog_logic_meta_history
     */
    public static final String META_DDL_RECORD_PERSIST_SQL_WITH_EXISTS =
        "meta.ddlrecord.persistSqlWithExist.switch";
    /**
     * binlog_logic_meta_history表数据量报警阈值
     */
    public static final String META_DDL_RECORD_LOGIC_COUNT_ALARM_THRESHOD =
        "meta.ddlrecord.logic.count.alarm.threshold";
    /**
     * binlog_phy_ddl_history表数据量报警阈值
     */
    public static final String META_DDL_RECORD_PHY_COUNT_ALARM_THRESHOD =
        "meta.ddlrecord.phy.count.alarm.threshold";
    /**
     * binlog_phy_ddl_history表数据清理阈值
     */
    public static final String META_DDL_RECORD_PHY_COUNT_CLEAN_THRESHOLD = "meta.ddlrecord.phy.count.clean.threshold";
    /**
     * 对__cdc_ddl_record__表中记录进行清理的阈值，当表中记录数高于参数设置时触发清理
     */
    public static final String META_DDL_RECORD_MARK_COUNT_CLEAN_THRESHOLD = "meta.ddlrecord.mark.count.clean.threshold";
    /**
     * 黑名单方式过滤cdc不需要关注的queryEvent, 如: grant,savepoint
     */
    public static final String META_QUERY_EVENT_BLACKLIST = "meta.queryEvent.blackList";

    /**
     * ddl sql中需要过滤掉的语法特性，多个特性之间用逗号风格
     * 举例：alter table vvv modify column b bigint after c ALGORITHM=OMC，需要把OMC去掉
     */
    public static final String META_DDL_CONVERTER_ALGORITHM_BLACKLIST = "meta.ddl.converter.algorithm.blacklist";
    /**
     * 元数据转储到磁盘时，根存储目录
     */
    public static final String META_PERSIST_BASE_PATH = "meta.persist.basePath";
    /**
     * 是否对SchemaObject进行持久化，默认false
     */
    public static final String META_PERSIST_SCHEMA_OBJECT_SWITCH = "meta.persist.schemaObject.switch";
    /**
     * 是否对Topology中的字符串进行share共享，默认false，DN数量非常多的时候建议开启，可节省大量内存
     */
    public static final String META_TOPOLOGY_SHARE_SWITCH = "meta.topology.share.switch";
    /**
     * 是否对Topology中的字符串进行intern处理，默认false，META_TOPOLOGY_SHARE_SWITCH设置为On时才生效
     * intern之后会更节省空间，但是intern操作很耗费CPU，需视情况决定是否开启，一般不许开启，靠对String进行堆内存共享已经可以节省很大空间
     */
    public static final String META_TOPOLOGY_SHARE_USE_INTERN = "meta.topology.share.useIntern";
    public static final String META_LOGIC_DDL_APPLY_DATABASE_BLACKLIST = "meta.logicDdl.apply.database.blacklist";
    public static final String META_LOGIC_DDL_APPLY_TABLE_BLACKLIST = "meta.logicDdl.apply.table.blacklist";
    public static final String META_LOGIC_DDL_APPLY_TSO_BLACKLIST = "meta.logicDdl.apply.tso.blacklist";
    public static final String META_COMPARE_CACHE_ENABLE = "meta.compare.cache.enable";
    public static final String META_DDL_IGNORE_APPLY_ERROR = "meta.ddl.ignoreApplyError";
    public static final String META_TABLE_MAX_CACHE_SIZE = "meta.table.maxCacheSize";
    public static final String META_TABLE_CACHE_EXPIRE_TIME_MINUTES = "meta.table.cacheExpireTime.minutes";
    //******************************************************************************************************************
    //***********************************************SQL闪回功能相关参数***************************************************
    //******************************************************************************************************************

    /**
     * 每个Task负担的Binlog文件的个数
     */
    public static final String FLASHBACK_BINLOG_FILES_COUNT_PER_TASK = "flashback.binlog.files.count.perTask";
    /**
     * RecoveryApplier写入缓冲区大小(sql的个数)
     */
    public static final String FLASHBACK_BINLOG_WRITE_BUFFER_SIZE = "flashback.binlog.write.bufferSize";
    /**
     * 从OSS下载binlog文件在本地保存路径
     */
    public static final String FLASHBACK_BINLOG_DOWNLOAD_DIR = "flashback.binlog.download.dir";
    /**
     * 对于新加入的节点，本地磁盘文件为空，需要从OSS或Lindorm下载逻辑Binlog文件，此参数配置可以下载的最大个数
     */
    public static final String FLASHBACK_BINLOG_MAX_DOWNLOAD_FILE_COUNT = "flashback.binlog.maxDownloadFileCount";
    /**
     * 从OSS下载Binlog文件的线程数
     */
    public static final String FLASHBACK_BINLOG_DOWNLOAD_THREAD_NUM = "flashback.binlog.downloadThreadNum";
    /**
     * SQL闪回结果文件下载链接有效时长，单位：秒
     */
    public static final String FLASHBACK_DOWNLOAD_LINK_AVAILABLE_INTERVAL = "flashback.downloadlink.available.interval";
    /**
     * SQL闪回上传接文件时，使用MultiUploadMode的阈值，单位：字节
     */
    public static final String FLASHBACK_UPLOAD_MULTI_MODE_THRESHOLD = "flashback.upload.multiMode.threshold";

    //******************************************************************************************************************
    //*********************************************** Replica相关参数 ***************************************************
    //******************************************************************************************************************

    public static final String RPL_PERSIST_BASE_PATH = "rpl.persist.basePath";

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
