/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getPropertiesValue;

/**
 * created by ziyang.lb
 **/
public abstract class ConfigKeys {

    //******************************************************************************************************************
    //***************************************************系统基础参数配置**************************************************
    //******************************************************************************************************************
    public static final String TASK_NAME = "taskName";

    public static final String IS_REPLICA = "is_replica";
    /**
     * Polarx主实例的id
     */
    public static final String POLARX_INST_ID = "polardbx_instance_id";

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
     * 分配给该实例的列存可用端口
     */
    public static final String COLUMNAR_PORT = "columnarPort";
    /**
     * 当前运行时的模式，Local or Cluster
     */
    public static final String RUNTIME_MODE = "runtime_mode";
    /**
     * 当前CDC实例所属用户的的uid
     */
    public static final String RDS_UID = "rds_uid";
    /**
     * 当前CDC实例所属用户的bid
     */
    public static final String RDS_BID = "rds_bid";
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
    public static final String PRINT_METRICS = "print_metrics";
    /**
     * 激活Daemon接口ACL
     */
    public static final String ENABLE_INTERFACE_ACL = "interface_acl_enabled";
    /**
     * deamon接口acl aksk
     */
    public static final String DAEMON_REST_API_ACL_AK = "daemon_rest_api_acl_ak";
    public static final String DAEMON_REST_API_ACL_SK = "daemon_rest_api_acl_sk";
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
    public static final String ASSIGNED_DN_IP = "assigned_dn_ip";

    public static final String SIGAR_ENABLED = "sigar_enabled";

    /**
     * 强制reset binlog开关,开发环境使用
     */
    public static final String FORCE_RESET_ENABLE = "force_reset_enable";

    /**
     * releaseNote路径， 单元测试和线上不同
     */
    public static final String RELEASE_NOTE_PATH = "release_note_path";
    /**
     * meta db 连接串
     */
    public static final String METADB_URL = "metaDb_url";
    public static final String METADB_USERNAME = "metaDb_username";
    public static final String METADB_PASSWORD = "metaDb_password";
    public static final String METADB_SCAN_SWITCH = "metadb_scan_switch";
    public static final String METADB_SCAN_INTERVAL_SECONDS = "metadb_scan_interval_seconds";

    /**
     * 最近一次获得的CN的地址在本地保存的文件名
     */
    public static final String LATEST_SERVER_ADDRESS_PERSIST_FILE = "latest_server_address_persist_file";

    /**
     * 最近一次获得的CN地址刷新到文件的时间间隔，单位：秒
     */
    public static final String LATEST_SERVER_ADDRESS_FLUSH_INTERVAL = "latest_server_address_flush_interval";

    /**
     * 检测连接是否可用的超时时间
     */
    public static final String DATASOURCE_CHECK_VALID_TIMEOUT_SEC = "datasource_check_valid_timeout_sec";
    public static final String DATASOURCE_CN_CONNECT_TIMEOUT_IN_SECOND = "datasource_cn_connect_timeout_in_second";
    public static final String DATASOURCE_CN_GET_TIMEOUT_IN_SECOND = "datasource_cn_get_timeout_in_second";
    /**
     * 集群角色
     */
    public static final String CLUSTER_ROLE = "cluster_role";
    /**
     * 是否是实验室测试环境
     */
    public static final String IS_LAB_ENV = "is_lab_env";

    //******************************************************************************************************************
    //********************************************Rpc Protocol协议交互相关配置**************************************
    //******************************************************************************************************************
    /**
     * TxnStream，对dump reply进行封包的方式，默认：BYTES，可以进行两阶段序列化，有更好的性能
     */
    public static final String BINLOG_TXN_STREAM_PACKET_MODE = "binlog_txn_stream_packet_mode";
    /**
     * TxnStream，是否使用异步模式，默认true，异步模式下序列化线程和binlog write线程各自独立，可以带来更有的性能
     */
    public static final String BINLOG_TXN_STREAM_CLIENT_ASYNC_ENABLE = "binlog_txn_stream_client_async_enabled";
    /**
     * TxnStream，使用异步模式时，接收队列的大小，默认256
     */
    public static final String BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE =
        "binlog_txn_stream_client_receive_queue_size";
    /**
     * TxnStream，Client端flow control window的大小
     */
    public static final String BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE =
        "binlog_txn_stream_flow_control_window_size";

    //******************************************************************************************************************
    //********************************************逻辑Binlog目录&文件&上传下载等相关配置**************************************
    //******************************************************************************************************************
    /**
     * 逻辑Binlog的保存目录
     */
    public static final String BINLOG_DIR_PATH = "binlog_dir_path";
    /**
     * 逻辑Binlog占用本地磁盘空间上限，单位：M
     */
    public static final String BINLOG_DISK_SPACE_MAX_SIZE_MB = "binlog_disk_max_size_mb";
    /**
     * 单个逻辑Binlog文件的大小,单位：字节
     */
    public static final String BINLOG_FILE_SIZE = "binlog_file_size";
    /**
     * 是否测试流式消费功能
     */
    public static final String BINLOG_DUMP_TEST_STREAMING_CONSUME_ENABLED =
        "binlog_dump_test_streaming_consume_enabled";
    /**
     * 强制使用流式消费
     */
    public static final String BINLOG_DUMP_FORCE_STREAMING_CONSUME_ENABLED =
        "binlog_dump_force_streaming_consume_enabled";
    /**
     * 对逻辑Binlog进行seek操作时的缓冲区大小，单位：M
     */
    public static final String BINLOG_FILE_SEEK_BUFFER_SIZE = "binlog_file_seek_buffer_size";
    /**
     * 对逻辑Binlog进行seek操作时，获取lastTso的模式，0-获取文件中的最后一个cts，1-获取文件中的最后一个事务策略为TSO的cts，默认0
     */
    public static final String BINLOG_FILE_SEEK_LAST_TSO_MODE = "binlog_file_seek_last_tso_mode";
    /**
     * 写binlog文件时是否开启dry run
     */
    public static final String BINLOG_WRITE_DRY_RUN_ENABLE = "binlog_write_dry_run_enabled";
    /**
     * dry run mode，0 - on receive，1 - on push，2 - on sink
     */
    public static final String BINLOG_WRITE_DRY_RUN_MODE = "binlog_write_dry_run_mode";
    /**
     * 写binlog文件时是否支持记录RowQueryLogEvent
     */
    public static final String BINLOG_WRITE_ROWS_QUERY_EVENT_ENABLE = "binlog_write_rows_query_event_enabled";
    /**
     * 写binlog文件时是否支持对rowsQuery的合法性进行校验
     */
    public static final String BINLOG_WRITE_CHECK_ROWS_QUERY_EVENT = "binlog_write_check_rows_query_event";
    /**
     * 写binlog文件时是否对server_id的合法性进行校验
     */
    public static final String BINLOG_WRITE_CHECK_SERVER_ID = "binlog_write_check_server_id";
    /**
     * 对server_id进行校验时，预期的目标值
     */
    public static final String BINLOG_WRITE_CHECK_SERVER_ID_TARGET_VALUE = "binlog_write_check_server_id_target_value";
    /**
     * 写binlog文件时是否支持校验tso的顺序和重复行
     */
    public static final String BINLOG_WRITE_CHECK_TSO = "binlog_write_check_tso";
    /**
     * 逻辑binlog写缓冲区的大小，单位字节，建议为2的倍数
     */
    public static final String BINLOG_WRITE_BUFFER_SIZE = "binlog_write_buffer_size";
    /**
     * 逻辑Binlog写入时，缓冲区是否使用直接内存，默认true
     */
    public static final String BINLOG_WRITE_BUFFER_DIRECT_ENABLE = "binlog_write_buffer_direct_enabled";
    /**
     * 逻辑Binlog Write Buffer中的数据flush的策略，0-每个事务flush一次，1-定时flush
     */
    public static final String BINLOG_WRITE_FLUSH_POLICY = "binlog_write_flush_policy";
    /**
     * 逻辑Binlog Write Buffer的flush的间隔（单位：毫秒），当flush策略为1时有效
     */
    public static final String BINLOG_WRITE_FLUSH_INTERVAL = "binlog_write_flush_interval";
    /**
     * 心跳刷盘频率，默认30秒
     */
    public static final String BINLOG_WRITE_HEARTBEAT_INTERVAL = "binlog_write_heartbeat_interval";
    /**
     * 当有binlog dump请求时，心跳刷盘的频率
     */
    public static final String BINLOG_WRITE_HEARTBEAT_INTERVAL_WITH_DUMP = "binlog_write_heartbeat_interval_with_dump";
    /**
     * 心跳是否以事务的形式记录
     */
    public static final String BINLOG_WRITE_HEARTBEAT_AS_TXN = "binlog_write_heartbeat_as_txn";
    /**
     * tableId的初始值
     */
    public static final String BINLOG_WRITE_TABLE_ID_BASE_VALUE = "binlog_write_tableid_base_value";
    /**
     * 是否开启流水线多线程并行写入，默认true
     */
    public static final String BINLOG_PARALLEL_BUILD_ENABLED = "binlog_parallel_build_enabled";
    /**
     * 开启并行写入时的并行度，默认2
     */
    public static final String BINLOG_PARALLEL_BUILD_PARALLELISM = "binlog_parallel_build_parallelism";
    /**
     * 开启并行写入时，是否使用batch模式，即是否使用BatchEventToken，默认：true
     * batch模式主要用来解决每个binlog event很小但量很大时的性能问题，非batch模式下，每个binlog event会占据ringbuffer的一个槽位，会导致
     * 生产者和消费之间过于频繁的线程上下文切换，通过使用batch模式，每个槽位对应的是一批binlog event，从而提升吞吐
     */
    public static final String BINLOG_PARALLEL_BUILD_WITH_BATCH = "binlog_parallel_build_with_batch";
    /**
     * 开启并行写入时，RingBuffer缓冲区的大小，默认65536
     */
    public static final String BINLOG_PARALLEL_BUILD_RING_BUFFER_SIZE = "binlog_parallel_build_ring_buffer_size";
    /**
     * 开启并行写入时，RingBuffer中每个slot槽位缓冲区的大小，默认1024 byte
     */
    public static final String BINLOG_PARALLEL_BUILD_MAX_SLOT_SIZE =
        "binlog_parallel_build_max_slot_size";
    /**
     * 开启并行写入时，RingBuffer中每个slot槽位所能持有的最大载荷，默认65536 byte，和BINLOG_PARALLEL_BUILD_MAX_SLOT_SIZE一起共同决定了
     * RingBuffer缓冲区占满时，所需要的最大内存空间
     */
    public static final String BINLOG_PARALLEL_BUILD_MAX_SLOT_PAYLOAD_SIZE =
        "binlog_parallel_build_max_slot_payload_size";
    /**
     * 读取binlog文件的缓冲区大小，单位字节，默认：10485760
     */
    public static final String BINLOG_SYNC_READ_BUFFER_SIZE = "binlog_sync_read_buffer_size";
    /**
     * dumper主备复制单个packet的最大大小，单位：字节
     */
    public static final String BINLOG_SYNC_PACKET_SIZE = "binlog_sync_packet_size";
    /**
     * dumper主备复制，客户端是否使用异步模式，默认：true，异步模式下packet反序列化和文件写入操作分属不同线程，有更好的性能表现
     */
    public static final String BINLOG_SYNC_CLIENT_ASYNC_ENABLE = "binlog_sync_client_async_enabled";
    /**
     * dumper主备复制，客户端接收队列的大小，默认：64，异步模式下有效
     */
    public static final String BINLOG_SYNC_CLIENT_RECEIVE_QUEUE_SIZE = "binlog_sync_client_receive_queue_size";
    /**
     * dumper主备复制，客户端flow control window的大小，单位：M，默认800M
     */
    public static final String BINLOG_SYNC_FLOW_CONTROL_WINDOW_SIZE =
        "binlog_sync_flow_control_window_size";
    /**
     * dumper主备复制，对event进行拆包的方式，默认client端拆包，可以有更好的性能
     */
    public static final String BINLOG_SYNC_EVENT_SPLIT_MODE = "binlog_sync_event_split_mode";

    public static final String BINLOG_SYNC_INJECT_TROUBLE = "binlog_sync_inject_trouble_enabled";

    public static final String BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND =
        "binlog_sync_check_file_status_interval_second";

    /**
     * dumper slave启动模式
     */
    public static final String BINLOG_RECOVER_MODE_WITH_DUMPER_SLAVE = "binlog_recover_mode_with_dumper_slave";

    /**
     * dumper对下游消费订阅，进行数据推送单个packet的最大大小，单位：字节
     */
    public static final String BINLOG_DUMP_PACKET_SIZE = "binlog_dump_packet_size";
    /**
     * dumper对下游消费订阅，从binlog文件进行数据读取缓冲区的大小，单位：字节
     */
    public static final String BINLOG_DUMP_READ_BUFFER_SIZE = "binlog_dump_read_buffer_size";
    /**
     * dumper对现有消费订阅，当长时间没有数据时，发送heartbeat的频率，默认1s
     * 该值的单位是纳秒
     */
    public static final String BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD = "binlog_dump_master_heartbeat_period";
    /**
     * dumper通过binlog dump发送出去的binlog event是否带checksum
     */
    public static final String BINLOG_DUMP_M_EVENT_CHECKSUM_ALG = "binlog_dump_m_event_checksum_alg";
    /**
     * dumper在进行binlog dump之前是否校验slave的checksum alg
     */
    public static final String BINLOG_DUMP_CHECK_CHECKSUM_ALG_SWITCH = "binlog_dump_check_checksum_alg_switch";
    /**
     * binlog dump触发反压控制时的休眠时间，单位微妙
     */
    public static final String BINLOG_DUMP_BACK_PRESSURE_SLEEP_TIME_US =
        "binlog_dump_back_pressure_sleep_time_us";
    /**
     * dumper 启动后等待 cursor ready重试间隔
     */
    public static final String BINLOG_DUMP_WAIT_CURSOR_READY_RETRY_INTERVAL_SECOND =
        "binlog_dump_wait_cursor_ready_retry_interval_second";

    /**
     * dumper 启动后得替代 cursor ready重试次数配置
     */
    public static final String BINLOG_DUMP_WAIT_CURSOR_READY_TIMES_LIMIT =
        "binlog_dump_wait_cursor_ready_times_limit";

    /**
     * dump下载本地路径
     */
    public static final String BINLOG_DUMP_DOWNLOAD_PATH = "binlog_dump_download_path";

    /**
     * 在dump之前是否需要先将文件从oss下载到本地
     */
    public static final String BINLOG_DUMP_DOWNLOAD_FIRST_MODE = "binlog_dump_download_first_mode";

    /**
     * dump下载的滑动窗口大小
     */
    public static final String BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE = "binlog_dump_download_window_size";

    /**
     * dump文件下载失败后的重试次数
     */
    public static final String BINLOG_DUMP_DOWNLOAD_ERROR_RETRY_TIMES = "binlog_dump_download_error_retry_times";

    public static final String BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS = "binlog_dump_download_max_wait_seconds";

    /**
     * 逻辑Binlog文件的备份方式，OSS or Lindorm or NULL
     */
    public static final String BINLOG_BACKUP_TYPE = "binlog_backup_type";
    /**
     * 逻辑Binlog文件在OSS 或 Lindorm 的过期时间，单位：天
     */
    public static final String BINLOG_BACKUP_FILE_PRESERVE_DAYS = "binlog_backup_file_preserve_days";
    /**
     * binlog_oss_record表中purge_status状态为COMPLETE的记录的保存时间，单位：天
     */
    public static final String BINLOG_BACKUP_PURGED_RECORD_PRESERVE_DAYS = "binlog_backup_purged_record_preserve_days";
    /**
     * 逻辑Binlog文件进行备份上传时，缓冲区的大小
     */
    public static final String BINLOG_BACKUP_UPLOAD_BUFFER_SIZE = "binlog_backup_upload_buffer_size";
    /**
     * 逻辑Binlog文件备份上传的最大执行线程数
     */
    public static final String BINLOG_BACKUP_UPLOAD_MAX_THREAD_NUM = "binlog_backup_upload_max_thread_num";
    /**
     * 上传Binlog文件到OSS或Lindorm的模式，默认为APPEND，Lindorm不支持Mutiple
     */
    public static final String BINLOG_BACKUP_UPLOAD_MODE = "binlog_backup_upload_mode";
    /**
     * 恢复时从远端存储下载文件的方式，见BinlogDownload.DownloadMode，OSS有分片并行下载和单个文件串行下载两种方式
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_MODE = "binlog_backup_download_mode";
    /**
     * 分片并行下载时每个分片的大小，单位：字节
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_PART_SIZE = "binlog_backup_download_part_size";
    /**
     * 分片并行下载时下载线程的数目
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_MAX_THREAD_NUM = "binlog_backup_download_max_thread_num";
    /**
     * 本地binlog为空时从远端下载的文件的个数
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_LAST_FILE_COUNT = "binlog_backup_download_last_file_count";
    /**
     * Binlog文件上传到备份存储的模式为APPEND时，最大可Append的FileSize，超过该Size将转化为Multiple模式，如果支持Multiple的话，单位：G
     */
    public static final String BINLOG_BACKUP_UPLOAD_MULTI_APPEND_THRESHOLD =
        "binlog_backup_upload_multi_append_threshold";
    /**
     * Binlog文件上传到备份存储的模式为APPEND时，最大可Append的FileSize，超过该Size将转化为Multiple模式，如果支持Multiple的话，单位：字节
     */
    public static final String BINLOG_BACKUP_UPLOAD_PART_SIZE = "binlog_backup_upload_part_size";
    /**
     * Binlog文件上传到备份存储，从本地文件fetch data时的超时时间，单位：ms
     */
    public static final String BINLOG_BACKUP_UPLOAD_WAIT_DATA_TIMEOUT_MS = "binlog_backup_upload_wait_data_timeout_ms";
    /**
     * CDC 逻辑binlog下载链接有效时长，单位：秒
     */
    public static final String BINLOG_BACKUP_DOWNLOAD_LINK_PRESERVE_SECONDS =
        "binlog_backup_download_link_preserve_seconds";
    /**
     * 过期逻辑Binlog文件，检测间隔，单位分钟
     */
    public static final String BINLOG_PURGE_CHECK_INTERVAL_MINUTE = "binlog_purge_check_interval_minute";
    /**
     * 触发本地清理的阈值，默认90%
     */
    public static final String BINLOG_PURGE_DISK_USE_RATIO = "binlog_purge_disk_use_ratio";
    /**
     * 是否支持对本地binlog进行清理
     */
    public static final String BINLOG_PURGE_ENABLE = "binlog_purge_enabled";
    /**
     * 通过该配置，可以对binlog文件中的last tso进行overwrite，格式 [old tso, overwritten tso]
     */
    public static final String BINLOG_RECOVER_TSO_OVERWRITE_CONFIG = "binlog_recover_tso_overwrite_config";
    public static final String BINLOG_DDL_SET_TABLE_GROUP_ENABLED = "binlog_ddl_set_table_group_enabled";
    public static final String BINLOG_DDL_ALTER_MANUALLY_TABLE_GROUP_ENABLED =
        "binlog_ddl_alter_manually_table_group_enabled";
    public static final String BINLOG_DDL_ALTER_IMPLICIT_TABLE_GROUP_ENABLED =
        "binlog_ddl_alter_implicit_table_group_enabled";

    /**
     * meta db leader ddl检测开关
     */
    public static final String BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE =
        "binlog_meta_leader_detect_by_ddl_mode_enable";

    /**
     * 是否忽略事务中的DDL语句
     */
    public static final String BINLOG_SKIP_DDL_IN_TRANSACTION = "binlog_skip_ddl_in_transaction";

    /**
     * 逻辑Binlog文件上传到OSS的accessKeyId
     */
    public static final String OSS_ACCESSKEY_ID = "oss_access_key_id";
    /**
     * 逻辑Binlog文件上传到OSS的accessKeySecret
     */
    public static final String OSS_ACCESSKEY_ID_SECRET = "oss_access_key_secret";
    /**
     * 逻辑Binlog文件上传到OSS的bucket
     */
    public static final String OSS_BUCKET = "oss_bucket_name";
    /**
     * 逻辑Binlog文件上传到OSS的endpoint
     */
    public static final String OSS_ENDPOINT = "oss_endpoint";
    /**
     * 逻辑Binlog文件上传到Lindorm的accessKeyId
     */
    public static final String LINDORM_ACCESSKEY_ID = "lindorm_access_key_id";
    /**
     * 逻辑Binlog文件上传到Lindorm的accessKeySecret
     */
    public static final String LINDORM_ACCESSKEY_ID_SECRET = "lindorm_access_key_secret";
    /**
     * 逻辑Binlog文件上传到Lindorm的bucket
     */
    public static final String LINDORM_BUCKET = "lindorm_bucket_name";
    /**
     * 逻辑Binlog文件上传到Lindorm的endpoint
     */
    public static final String LINDORM_ENDPOINT = "lindorm_endpoint";
    /**
     * lindorm的thrift服务端口
     */
    public static final String LINDORM_THRIFT_PORT = "lindorm_thrift_port";
    /**
     * lindorm的aws s3服务端口
     */
    public static final String LINDORM_S3_PORT = "lindorm_s3_port";

    //******************************************************************************************************************
    //***************************************************Task运行时相关的配置**********************************************
    //******************************************************************************************************************

    /**
     * 在task进行bootstrap的过程中，是否自动启动task engine内核，默认为false
     */
    public static final String TASK_ENGINE_AUTO_START = "task_engine_auto_start";
    /**
     * extractor是否记录每个事务的信息，用于数据审计，默认false
     */
    public static final String TASK_EXTRACT_LOG_TRANS = "task_extract_log_trans";
    /**
     * extractor是否记录每个事务的详细信息，用于数据审计，默认false
     */
    public static final String TASK_EXTRACT_LOG_TRANS_DETAIL = "task_extract_log_trans_detail";
    /**
     * sorter模块积攒多少个Transaction之后，才可以对外输出，一般用来模拟超长事务空洞
     */
    public static final String TASK_EXTRACT_SORT_HOLD_SIZE = "task_extract_sort_hold_size";
    /**
     * transaction group的大小，当触发了Transaction的持久化之后，该参数可以有效控制Transaction反序列化的批次大小，在非持久化场景下，该参数意义不大
     */
    public static final String TASK_EXTRACT_TRANSACTION_GROUP_SIZE = "task_extract_transaction_group_size";
    /**
     * 是否开启针对transaction对象的内存泄漏
     */
    public static final String TASK_EXTRACT_WATCH_MEMORY_LEAK_ENABLED = "task_extract_watch_memory_leak_enabled";
    /**
     * 逻辑表黑名单，黑名单中的表会被过滤掉，格式：dbname1.tbname1,dbname1.tbname2,dbname2.tbname1,...
     */
    public static final String TASK_EXTRACT_FILTER_LOGIC_TABLE_BLACKLIST = "task_extract_filter_logic_table_blacklist";
    /**
     * 逻辑库黑名单，黑名单中的库会被过滤掉，格式: dbname1,dbname2,dbname3,...
     */
    public static final String TASK_EXTRACT_FILTER_LOGIC_DB_BLACKLIST = "task_extract_filter_logic_db_blacklist";
    /**
     * 任务整形数据Log开关
     */
    public static final String TASK_EXTRACT_REBUILD_DATA_LOG = "task_extract_rebuild_data_log";
    /**
     * 是否强制从backup存储下载rds binlog并消费
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_FORCED = "task_dump_offline_binlog_forced";
    /**
     * rds binlog下载到本地的目录
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR = "task_dump_offline_binlog_download_dir";
    /**
     * 从oss下载rds binlog时，下载最近多少天的binlog文件 RDS_PREFER_HOST_MAP
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_RECALL_DAYS_LIMIT =
        "task_dump_offline_binlog_recall_days_limit";
    /**
     * 下载Binlog文件时，指定所属的host
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_PREFER_HOST_INSTANCES =
        "task_dump_offline_binlog_prefer_host_instances";
    /**
     * 下载Binlog文件时，并行下载的基础线程数
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_THREAD_INIT_NUM =
        "task_dump_offline_binlog_download_thread_init_num";
    /**
     * 下载Binlog文件时，所能使用的磁盘限制
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_TOTAL =
        "task_dump_offline_binlog_download_disk_limit_total";

    /**
     * 下载Binlog文件时，单个DN所能使用的磁盘限制
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_PER_DN =
        "task_dump_offline_binlog_download_disk_limit_per_dn";
    /**
     * 下载Binlog文件时，所能使用的线程限制
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_THREAD_MAX_NUM =
        "task_dump_offline_binlog_download_thread_max_num";
    /**
     * merger是否开启dry run，开启的话则不会向collector发送数据
     */
    public static final String TASK_MERGE_DRY_RUN = "task_merge_dry_run";
    /**
     * merger所处的dry-run模式:
     * 0- not push token to merge source
     * 1- before merge barrier
     * 2- after merge barrier
     */
    public static final String TASK_MERGE_DRY_RUN_MODE = "task_merge_dry_run_mode";
    /**
     * merger是否合并事务策略不是Tso的Xa事务，目前只能设置为false
     */
    public static final String TASK_MERGE_XA_WITHOUT_TSO = "task_merge_xa_without_tso";
    /**
     * merge group unit size
     */
    public static final String TASK_MERGE_GROUP_UNIT_SIZE = "task_merge_group_unit_size";
    /**
     * merge group queue size
     */
    public static final String TASK_MERGE_GROUP_QUEUE_SIZE = "task_merge_group_queue_size";
    /**
     * merge group max level size
     */
    public static final String TASK_MERGE_GROUP_MAX_LEVEL = "task_merge_group_max_level";
    /**
     * 心跳窗口被强制force complete的阈值
     */
    public static final String TASK_MERGE_FORCE_COMPLETE_HEARTBEAT_WINDOW_TIME_LIMIT =
        "task_merge_force_complete_heartbeat_window_time_limit";

    public static final String TASK_MERGE_CHECK_HEARTBEAT_WINDOW_ENABLED = "task_merge_check_heartbeat_window_enabled";

    /**
     * transmitter发送数据时的组包模式，ITEMSIZE or MEMSIZE
     */
    public static final String TASK_TRANSMIT_CHUNK_MODE = "task_transmit_chunk_mode";
    /**
     * transmitter发送数据包时，每个Chunk的ItemSize
     */
    public static final String TASK_TRANSMIT_CHUNK_ITEM_SIZE = "task_transmit_chunk_item_size";
    /**
     * 每个DumpReply包含的TxnMessage的最大大小，默认值为100M，单位：Byte
     */
    public static final String TASK_TRANSMIT_MAX_MESSAGE_SIZE = "task_transmit_max_message_size";
    /**
     * collector merge阶段的并行度
     */
    public static final String TASK_COLLECT_MERGE_STAGE_PARALLELISM = "task_collect_merge_stage_parallelism";
    /**
     * 在collector阶段构建DumpReply Packet的阈值，默认65536(单位：字节)，TxnBuffer size小于该阈值时，会在collector阶段构建packet，以提升性能
     */
    public static final String TASK_COLLECT_BUILD_PACKET_SIZE_LIMIT = "task_collect_build_packet_size_limit";
    /**
     * transmitter是否开启dry run，默认false
     */
    public static final String TASK_TRANSMIT_DRY_RUN = "task_transmit_dry_run";
    /**
     * transmitter dry run mode, 0 - before dumping queue ,1 - before send to dumper
     */
    public static final String TASK_TRANSMIT_DRY_RUN_MODE = "task_transmit_dry_run_mode";
    /**
     * 是否支持binlog中显示隐藏主键
     */
    public static final String TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED =
        "task_reformat_attach_drds_hidden_pk_enabled";
    /**
     * 是否忽略TraceId乱序，默认false
     */
    public static final String TASK_EXTRACT_DISORDER_TRACE_ID_ALLOWED = "task_extract_disorder_trace_id_allowed";
    /**
     * 忽略buffer key重复异常
     */
    public static final String TASK_EXTRACT_SKIP_DUPLICATE_TXN_KEY = "task_extract_skip_duplicate_txn_key";
    /**
     * 对事务进行跳过的白名单，多个事务用#分隔
     */
    public static final String TASK_EXTRACT_FILTER_TRANS_BLACKLIST = "task_extract_filter_trans_blacklist";
    /**
     * 事务白名单动态跳过堆积检测阈值
     */
    public static final String TASK_EXTRACT_FILTER_TRANS_THRESHOLD = "task_extract_filter_trans_threshold";
    /**
     * merge source queue的大小，默认1024
     */
    public static final String TASK_MERGE_SOURCE_QUEUE_SIZE = "task_merge_source_queue_size";
    /**
     * 所有merge source queue的size求和之后的最大限制值，防止过多导致内存吃紧，比如对接1024个DN的场景，如果每个mergesource的queue size还是1024
     */
    public static final String TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE = "task_merge_source_queue_max_total_size";
    /**
     * collector queue的大小
     */
    public static final String TASK_COLLECT_QUEUE_SIZE = "task_collect_queue_size";
    /**
     * transmitter queue的大小
     */
    public static final String TASK_TRANSMIT_QUEUE_SIZE = "task_transmit_queue_size";
    public static final String TASK_TRANSMIT_DUMPING_QUEUE_SIZE = "task_transmit_dumping_queue_size";
    /**
     * search tso时，是否必须要求search到的rollback tso必须大于等于上一次ScaleOut或ScaleIn的Tso，默认为false
     * 主要用在测试环境，同一个storage被反复add和remove时，需保证rollback tso大于等于最近一次StorageChange的tso，否则构建出来的元数据有问题
     * 未经过充分完整测试，生产环境慎用
     */
    public static final String TASK_RECOVER_CHECK_PREVIOUS_DN_CHANGE =
        "task_recover_check_previous_dn_change";

    /**
     * 当RDS或者源Mysql ddl中包含hints时，如果解析报错，可以通过次开关remove掉hints
     */
    public static final String TASK_EXTRACT_REMOVE_HINTS_IN_DDL_SQL = "task_extract_remove_hints_in_ddl_sql";

    /**
     * 快速搜索模式， 正常搜索倒序每个文件都搜一把，快速模式忽略跨文件事务
     */
    public static final String TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE = "task_recover_search_tso_in_quick_mode";

    /**
     * 当不支持对逻辑binlog进行备份恢复时，Task在链路恢复阶段搜索tso时是否强制使用quick mode，以加快链路恢复速度
     */
    public static final String TASK_RECOVER_FORCE_QUICK_SEARCH_WHEN_LOSS_BACKUP =
        "task_recover_force_quick_search_when_loss_backup";

    /**
     * 打开 event 处理log
     */
    public static final String TASK_EXTRACT_LOG_EVENT = "task_extract_log_event";

    /**
     * 采用老版本下载模式
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE = "task_dump_offline_binlog_in_download_mode";

    /**
     * 搜索位点使用v1算法
     */
    public static final String TASK_RECOVER_SEARCH_TSO_WITH_V_1_ALGORITHM = "task_recover_search_tso_with_v1_algorithm";
    /**
     * oss 下载cache大小
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE = "task_dump_offline_binlog_cache_unit_size";
    /**
     * oss 模式，默认自动模式，根据dn数量判断 MEMORY/DISK/AUTO
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_CACHE_MODE = "task_dump_offline_binlog_cache_mode";
    /**
     * oss 内存最大使用量，30M * 3 * 6 = 540M，非磁盘模式使用
     */
    public static final String TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT = "task_dump_offline_binlog_cache_size_limit";

    /**
     * 列 类型整形支持
     */
    public static final String TASK_REFORMAT_COLUMN_TYPE_ENABLED =
        "task_reformat_column_type_enabled";

    /**
     * 私有DDL开关
     */
    public static final String TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED = "task_reformat_attach_private_ddl_enabled";

    /**
     * 强制整形开关
     */
    public static final String TASK_REFORMAT_EVENT_FORCE_ENABLED = "task_reformat_event_force_enabled";

    /**
     * 忽略掉元数据与物理数据列不相等异常
     */
    public static final String TASK_REFORMAT_IGNORE_MISMATCHED_COLUMN_ERROR =
        "task_reformat_ignore_mismatched_column_error";

    /**
     * 库表黑名单，可以过滤掉某些不需要的表,在加入到buffer（整形）之前, 示例: test_db_.my_table, 支持正则
     */
    public static final String TASK_EXTRACT_FILTER_PHYSICAL_TABLE_BLACKLIST =
        "task_extract_filter_physical_table_blacklist";

    /**
     * 全局Binlog slave集群的Task是否要拉取相同地域DN的binlog
     */
    public static final String TASK_DUMP_SAME_REGION_STORAGE_BINLOG = "task_dump_same_region_storage_binlog";

    /**
     * task 默认是否开启外键检查
     */
    public static final String TASK_REFORMAT_NO_FOREIGN_KEY_CHECK = "task_reformat_no_foreign_key_check";

    public static final String TASK_EXTRACT_CHECK_SYNC_POINT_ENABLED = "task_extract_check_sync_point_enabled";

    public static final String TASK_PROCESS_SYNC_POINT_WAIT_TIMEOUT_MILLISECOND =
        "task_process_sync_point_wait_timeout_millisecond";

    //******************************************************************************************************************
    //***********************************************Daemon和调度相关参数*************************************************
    //******************************************************************************************************************

    /**
     * Daemon进程执行心跳的间隔
     */
    public static final String DAEMON_HEARTBEAT_INTERVAL_MS = "daemon_heartbeat_interval_ms";
    /**
     * Damone Leader进程监测是否需要重新构建拓扑的时间隔
     */
    public static final String DAEMON_WATCH_CLUSTER_INTERVAL_MS = "daemon_watch_cluster_interval_ms";
    /**
     * Daemon监控Task或Dumper是否健康的时间间隔
     */
    public static final String DAEMON_WATCH_WORK_PROCESS_INTERVAL_MS = "daemon_watch_work_process_interval_ms";
    /**
     * Daemon判读Task或Dumper是否已经心跳超时的阈值
     */
    public static final String DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS =
        "daemon_watch_work_process_heartbeat_timeout_ms";
    /**
     * 对非本机的task进行
     */
    public static final String DAEMON_WATCH_WORK_PROCESS_BLACKLIST = "daemon_watch_work_process_blacklist";
    /**
     * Daemon leader节点执行TSO心跳的时间间隔
     */
    public static final String DAEMON_TSO_HEARTBEAT_INTERVAL_MS = "daemon_tso_heartbeat_interval_ms";
    /**
     * 用来配置TSO心跳间隔是否支持自适应模式，默认：true
     * 自适应模式主要用来提升性能，在大部分事务都是单机事务时，eps的性能瓶颈会受限于tso心跳的频率
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_ENABLE =
        "daemon_tso_heartbeat_self_adaption_enabled";
    /**
     * TSO心跳间隔开启自适应模式时，心跳频率自动调整的目标值，默认：10ms，设定的值要比默认值小
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL =
        "daemon_tso_heartbeat_self_adaption_target_interval";
    /**
     * TSO心跳间隔开启自适应模式时，触发自动调整的EPS阈值，该阈值之下保持默认心跳间隔，该阈值之上调整心跳间隔为目标值
     */
    public static final String DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_EPS_THRESHOLD =
        "daemon_tso_heartbeat_self_adaption_eps_threshold";
    /**
     * 对ddl相关维度的数据进行监控检测的周期频率
     */
    public static final String DAEMON_WATCH_HISTORY_RESOURCE_INTERVAL_MINUTE =
        "daemon_watch_history_resource_interval_minute";
    /**
     * 是否支持在binlog集群，运行Replica
     */
    public static final String DAEMON_WATCH_REPLICA_IN_BINLOG_CLUSTER_ENABLE =
        "daemon_watch_replica_in_binlog_cluster_enabled";
    /**
     * Daemon收到stop master之后，等待所有task暂停的超时时间
     */
    public static final String DAEMON_WAIT_TASK_STOP_TIMEOUT_SECOND = "daemon_wait_task_stop_timeout_second";
    /**
     * Daemon收到start master之后，等待所有task启动的超时时间
     */
    public static final String DAEMON_WAIT_TASK_START_TIMEOUT_SECOND = "daemon_wait_task_start_timeout_second";
    /**
     * Daemon master收到clean binlog之后，给daemon slave发送clean binlog，等待请求返回的超时时间
     */
    public static final String DAEMON_WAIT_CLEAN_BINLOG_TIMEOUT_SECOND = "daemon_wait_clean_binlog_timeout_second";
    /**
     * dn 健康检测间隔
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_INTERVAL = "daemon_dn_health_checker_interval";
    /**
     * dn 健康检测开关
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_SWITCH = "daemon_dn_health_checker_switch";
    /**
     * dn 健康检测链接超时时间
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_CONNECTION_TIMEOUT_SECOND =
        "daemon_dn_health_checker_connection_timeout_second";
    /**
     * dn 健康检测sla超时时间
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_SLA_LIMIT_SECOND = "daemon_dn_health_checker_sla_limit_second";

    /**
     * dn 健康检测故障植入开关
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_ERROR_INJECT = "daemon_dn_health_checker_error_inject";

    /**
     * dn 健康检测 连通性检测重试次数
     */
    public static final String DAEMON_DN_HEALTH_CHECKER_RETRY_NUM = "daemon_dn_health_checker_retry_num";

    /**
     * 启动自动flush log测试开关
     */
    public static final String DAEMON_AUTO_FLUSH_LOG_TEST = "daemon_auto_flush_log_test";

    /**
     * Daemon clean接口是否强制检查集群参数
     */
    public static final String DAEMON_CLEAN_INTERFACE_FORCE_CHECK_CLUSTER_ENABLED =
        "daemon_clean_interface_force_check_cluster_enabled";

    /**
     * 进行拓扑构建时，对节点最小数量的要求
     */
    public static final String TOPOLOGY_NODE_MINSIZE = "topology_node_minsize";
    /**
     * 触发重新进行拓扑构建的心跳超时阈值，心跳指的是Daemon的心跳
     */
    public static final String DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS = "daemon_watch_cluster_heartbeat_timeout_ms";
    /**
     * 重建构建拓扑后，等待task或dumper启动成功的超时时间，未发生超时前不会进行下一轮的拓扑重建
     */
    public static final String DAEMON_WATCH_CLUSTER_WAIT_START_TIMEOUT_MS =
        "daemon_watch_cluster_wait_start_timeout_ms";
    /**
     * 拓扑中的组件Dumper&Task，执行心跳的时间间隔
     */
    public static final String TOPOLOGY_WORK_PROCESS_HEARTBEAT_INTERVAL_MS =
        "topology_work_process_heartbeat_interval_ms";
    /**
     * 触发使用relay task的阈值，即DN数量达到多少之后，构建拓扑时会考虑relay task
     */
    public static final String TOPOLOGY_USE_RELAY_TASK_THRESHOLD_WITH_DN_NUM =
        "topology_use_relay_task_threshold_with_dn_num";
    /**
     * 构建拓扑时，可以使用该机器上内存资源的比率
     */
    public static final String TOPOLOGY_RESOURCE_USE_RATIO = "topology_resource_use_ratio";
    /**
     * 分配资源时，分配给Dumper的比重
     */
    public static final String TOPOLOGY_RESOURCE_DUMPER_WEIGHT = "topology_resource_dumper_weight";
    /**
     * Dumper slave可以使用的最大内存，默认8G，让出更多内存给Task
     */
    public static final String TOPOLOGY_RESOURCE_DUMPER_SLAVE_MAX_MEM = "topology_resource_dumper_slave_max_mem";
    /**
     * 分配资源时，分配给Task的比重
     */
    public static final String TOPOLOGY_RESOURCE_TASK_WEIGHT = "topology_resource_task_weight";
    /**
     * 兼容性开关，收到scaleout或者scalein打标信息时，是否对storage list进行修复
     */
    public static final String TOPOLOGY_REPAIR_STORAGE_WITH_SCALE_ENABLE = "topology_repair_storage_with_scale_enabled";
    /**
     * recover tso的来源，binlog_oss_record表或者latest_cursor
     */
    public static final String TOPOLOGY_RECOVER_TSO_TYPE = "topology_recover_tso_type";
    /**
     * 使用recover tso功能的最早恢复时间，单位：小时
     */
    public static final String TOPOLOGY_RECOVER_TSO_TIME_LIMIT = "topology_recover_tso_time_limit";
    /**
     * 使用recover tso最多可以恢复的binlog个数
     */
    public static final String TOPOLOGY_RECOVER_TSO_BINLOG_NUM_LIMIT = "topology_recover_tso_binlog_num_limit";
    /**
     * 是否测试recover tso功能
     */
    public static final String TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED = "topology_force_use_recover_tso_enabled";
    /**
     * 是否测试binlog下载功能
     */
    public static final String BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED = "binlog_backup_force_download_enabled";
    /**
     * 是否按时间清理本地binlog文件
     */
    public static final String BINLOG_BACKUP_PURGE_LOCAL_BY_TIME = "binlog_backup_purge_local_by_time";

    /**
     * RecoverTsoTestTimeTask强制刷新拓扑的时间间隔，如果需要定时重刷，设置为0
     */
    public static final String DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL = "daemon_force_refresh_topology_interval";
    /**
     * 当只有daemon进程不可用，但daemon所在容器上的Task或Dumper进程运行正常时，是否需要进行拓扑重建，默认false
     */
    public static final String DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN =
        "daemon_support_refresh_topology_only_daemon_down";

    //******************************************************************************************************************
    //***********************************************报警 &报警事件相关参数*************************************************
    //******************************************************************************************************************
    /**
     * dumper或task长时间未收到数据，触发报警的时间阈值
     */
    public static final String ALARM_NODATA_THRESHOLD_SECOND = "alarm_nodata_threshold_second";
    /**
     * dumper触发延迟报警的阈值，单位：S
     */
    public static final String ALARM_DELAY_THRESHOLD_SECOND = "alarm_delay_threshold_second";
    /**
     * 是否上报异常事件
     */
    public static final String ALARM_REPORT_ALARM_EVENT_ENABLED = "alarm_report_alarm_event_enabled";
    /**
     * 最近一次消费时间，单位为ms
     */
    public static final String ALARM_LATEST_CONSUME_TIME_MS = "alarm_latest_consume_time_ms";
    /**
     * 消费情况检查间隔，单位为ms
     */
    public static final String ALARM_CHECK_CONSUMER_INTERVAL_MS = "alarm_check_consumer_interval_ms";
    /**
     * 根据消费情况，升级报警的时间阈值，单位为ms
     */
    public static final String ALARM_FATAL_THRESHOLD_MS = "alarm_fatal_threshold_ms";

    //******************************************************************************************************************
    //***********************************************KV 存储相关参数******************************************************
    //******************************************************************************************************************

    /**
     * 持久化功能是否开启，默认开启
     */
    public static final String STORAGE_PERSIST_ENABLE = "storage_persist_enabled";
    /**
     * 是否支持对Transaction 对象进行持久化
     */
    public static final String STORAGE_PERSIST_TXN_ENTITY_ENABLED = "storage_persist_txn_entity_enabled";
    /**
     * 是否强制走磁盘模式
     */
    public static final String STORAGE_PERSIST_MODE = "storage_persist_mode";
    /**
     * 磁盘存储目录
     */
    public static final String STORAGE_PERSIST_BASE_PATH = "storage_persist_base_path";
    /**
     * 通过检测内存阈值决定是否需要触发落盘的频率，单位：ms，默认10ms一次
     */
    public static final String STORAGE_PERSIST_CHECK_INTERVAL_MILLS = "storage_persist_check_interval_mills";
    /**
     * 对存量TxnBuffer进行磁盘转储的阈值，默认阈值95%(0.95)
     */
    public static final String STORAGE_PERSIST_ALL_THRESHOLD = "storage_persist_all_threshold";
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
    public static final String STORAGE_PERSIST_NEW_THRESHOLD = "storage_persist_new_threshold";
    /**
     * 单个事务触发磁盘存储的阈值，超过指定的阈值，触发落盘，单位：字节
     */
    public static final String STORAGE_PERSIST_TXN_THRESHOLD = "storage_persist_txn_threshold";
    /**
     * 单个Event触发磁盘存储的阈值，超过指定的阈值，触发落盘，单位：字节
     */
    public static final String STORAGE_PERSIST_TXNITEM_THRESHOLD = "storage_persist_txnitem_threshold";
    /**
     * 磁盘中数据的删除模式
     */
    public static final String STORAGE_PERSIST_DELETE_MODE = "storage_persist_delete_mode";
    /**
     * 磁盘存储单元的个数
     */
    public static final String STORAGE_PERSIST_UNIT_COUNT = "storage_persist_unit_count";
    /**
     * 清理存储资源的线程个数
     */
    public static final String STORAGE_CLEAN_WORKER_COUNT = "storage_clean_worker_count";
    /**
     * 清理存储资源的缓冲区大小
     */
    public static final String STORAGE_CLEAN_BUFFER_SIZE = "storage_clean_buffer_size";
    /**
     * 对数据进行restore时是否支持并行操作，默认true
     */
    public static final String STORAGE_PARALLEL_RESTORE_ENABLE = "storage_parallel_restore_enabled";
    /**
     * 进行并行restore时的并发度，默认4
     */
    public static final String STORAGE_PARALLEL_RESTORE_PARALLELISM = "storage_parallel_restore_parallelism";
    /**
     * 进行并行restore时，每个批次的TxnItem条数，默认200，不能太大，否则有内存溢出风险
     */
    public static final String STORAGE_PARALLEL_RESTORE_BATCH_SIZE = "storage_parallel_restore_batch_size";
    /**
     * 进行并行restore时，eventSize的最大值，如果大于该阈值，则不进行restore操作
     */
    public static final String STORAGE_PARALLEL_RESTORE_MAX_EVENT_SIZE = "storage_parallel_restore_max_event_size";

    //******************************************************************************************************************
    //***********************************************Polarx库表元数据相关参数**********************************************
    //******************************************************************************************************************

    /**
     * 是否优先使用HistoryTable中的记录构建Repository，修复数据场景使用
     */
    public static final String META_BUILD_APPLY_FROM_HISTORY_FIRST = "meta_build_apply_from_history_first";

    /**
     * 是否优先使用__cdc_ddl_record__中的记录构建Repository，修复数据场景使用
     */
    public static final String META_BUILD_APPLY_FROM_RECORD_FIRST = "meta_build_apply_from_record_first";
    /**
     * Rollback时，元数据的构建方式
     */
    public static final String META_RECOVER_ROLLBACK_MODE = "meta_recover_rollback_mode";
    /**
     * 查询不到物理表元数据时，支持instant create的rollback mode
     */
    public static final String META_RETRIEVE_INSTANT_CREATE_TABLE_MODES =
        "meta_retrieve_instant_create_table_modes";
    /**
     * 每次执行完逻辑DDL后，是否对该DDL对应的表的元数据信息进行一致性检查
     */
    public static final String META_BUILD_CHECK_CONSISTENCY_ENABLED = "meta_build_check_consistency_enabled";
    /**
     * 每次执行完apply后是否打印当前表的详细信息
     */
    public static final String META_BUILD_LOG_TABLE_META_DETAIL_ENABLED = "meta_build_log_table_meta_detail_enabled";
    /**
     * 是否支持semi snapshot，默认为true，注：即使设置为false，rollback mode也是可以设置为SNAPSHOT_SEMI的，找不到semi记录会自动切换为SNAPSHOT_EXACTLY
     * 即：该参数和rollback mode没有约束关系，rollback mode即使没有使用SNAPSHOT_SEMI，仍然可以对semi_snapshot进行更新维护
     */
    public static final String META_BUILD_SEMI_SNAPSHOT_ENABLED = "meta_build_semi_snapshot_enabled";
    /**
     * SemiSnapshot的保存时间，超过该时间的snapshot会被移除，单位：小时
     */
    public static final String META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS = "meta_build_semi_snapshot_preserve_hours";
    /**
     * 对deltaChangeMap中全量进行一致性检测的周期，单位：秒
     */
    public static final String META_BUILD_SEMI_SNAPSHOT_CHECK_DELTA_INTERVAL_SEC =
        "meta_build_semi_snapshot_check_delta_interval_sec";
    /**
     * 当未发生实际schema变更时，是否记录create if not exists 或者 drop if exists 到 binlog_logic_meta_history
     */
    public static final String META_BUILD_RECORD_SQL_WITH_EXISTS_ENABLED =
        "meta_build_record_sql_with_exists_enabled";

    public static final String META_BUILD_RECORD_IGNORED_DDL_ENABLED = "meta_build_record_ignored_ddl_enabled";
    /**
     * 逻辑ddl 软删除支持
     */
    public static final String META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED =
        "meta_purge_logic_ddl_soft_delete_enabled";
    /**
     * binlog_logic_meta_history表数据量报警阈值
     */
    public static final String META_ALARM_LOGIC_DDL_COUNT_THRESHOLD =
        "meta_alarm_logic_ddl_count_threshold";
    /**
     * binlog_phy_ddl_history表数据量报警阈值
     */
    public static final String META_ALARM_PHYSICAL_DDL_COUNT_THRESHOLD =
        "meta_alarm_physical_ddl_count_threshold";
    /**
     * binlog_phy_ddl_history表数据清理阈值
     */
    public static final String META_PURGE_PHYSICAL_DDL_THRESHOLD = "meta_purge_physical_ddl_threshold";
    /**
     * 对__cdc_ddl_record__表中记录进行清理的阈值，当表中记录数高于参数设置时触发清理
     */
    public static final String META_PURGE_MARK_DDL_THRESHOLD = "meta_purge_mark_ddl_threshold";
    /**
     * 黑名单方式过滤cdc不需要关注的queryEvent, 如: grant,savepoint
     */
    public static final String META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX =
        "meta_build_physical_ddl_sql_blacklist_regex";

    /**
     * sql parse 后，case by case的方式过滤cdc不需要关注的queryEvent,parse报错后的处理方式
     */
    public static final String META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_FILTER_IGNORE_PARSE_ERROR =
        "meta_build_physical_ddl_sql_blacklist_filter_ignore_parse_error";
    /**
     * meta rebuild 故障植入
     */
    public static final String META_BUILD_SNAPSHOT_ERROR_INJECT =
        "meta_build_snapshot_error_inject";
    /**
     * ddl sql中需要过滤掉的语法特性，多个特性之间用逗号风格
     * 举例：alter table vvv modify column b bigint after c ALGORITHM=OMC，需要把OMC去掉
     */
    public static final String TASK_REFORMAT_DDL_ALGORITHM_BLACKLIST = "task_reformat_ddl_algorithm_blacklist";
    public static final String TASK_REFORMAT_DDL_HINT_BLACKLIST = "task_reformat_ddl_hint_blacklist";
    /**
     * 元数据转储到磁盘时，根存储目录
     */
    public static final String META_PERSIST_BASE_PATH = "meta_persist_base_path";
    /**
     * 是否对SchemaObject进行持久化，默认false
     */
    public static final String META_PERSIST_ENABLED = "meta_persist_enabled";
    /**
     * 是否对Topology中的字符串进行share共享，默认false，DN数量非常多的时候建议开启，可节省大量内存
     */
    public static final String META_BUILD_SHARE_TOPOLOGY_ENABLED = "meta_build_share_topology_enabled";
    /**
     * 是否对Topology中的字符串进行intern处理，默认false，META_TOPOLOGY_SHARE_SWITCH设置为On时才生效
     * intern之后会更节省空间，但是intern操作很耗费CPU，需视情况决定是否开启，一般不许开启，靠对String进行堆内存共享已经可以节省很大空间
     */
    public static final String META_BUILD_SHARE_TOPOLOGY_WITH_INTERN = "meta_build_share_topology_with_intern";
    public static final String META_BUILD_LOGIC_DDL_DB_BLACKLIST = "meta_build_logic_ddl_db_blacklist";
    public static final String META_BUILD_LOGIC_DDL_TABLE_BLACKLIST = "meta_build_logic_ddl_table_blacklist";
    public static final String META_BUILD_LOGIC_DDL_TSO_BLACKLIST = "meta_build_logic_ddl_tso_blacklist";
    public static final String META_CACHE_COMPARE_RESULT_ENABLED = "meta_cache_compare_result_enabled";
    public static final String META_BUILD_IGNORE_APPLY_ERROR = "meta_build_ignore_apply_error";
    public static final String META_CACHE_TABLE_META_MAX_SIZE = "meta_cache_table_meta_max_size";
    public static final String META_CACHE_TABLE_MEAT_EXPIRE_TIME_MINUTES = "meta_cache_table_meat_expire_time_minutes";
    /**
     * table meta 检测周期
     */
    public static final String META_BUILD_FULL_SNAPSHOT_CHECK_INTERVAL_SEC =
        "meta_build_full_snapshot_check_interval_sec";

    public static final String META_WRITE_ALL_VARIABLE_TO_DB_SWITCH =
        "meta_write_all_variable_to_db_switch";
    /**
     * 恢复模式是否强制使用精确snapshot
     */
    public static final String META_RECOVER_FORCE_USE_SNAPSHOT_EXACTLY_MODE =
        "meta_recover_force_use_snapshot_exactly_mode";

    //******************************************************************************************************************
    //***********************************************SQL闪回功能相关参数***************************************************
    //******************************************************************************************************************

    /**
     * 每个Task负担的Binlog文件的个数
     */
    public static final String FLASHBACK_BINLOG_FILES_COUNT_PER_TASK = "flashback_binlog_files_count_per_task";
    /**
     * RecoveryApplier写入缓冲区大小(sql的个数)
     */
    public static final String FLASHBACK_BINLOG_WRITE_BUFFER_SQL_SIZE = "flashback_binlog_write_buffer_sql_size";
    /**
     * RecoveryApplier写入缓冲区大小(字节数)
     */
    public static final String FLASHBACK_BINLOG_WRITE_BUFFER_BYTE_SIZE = "flashback_binlog_write_buffer_byte_size";
    /**
     * 从OSS下载binlog文件在本地保存路径
     */
    public static final String FLASHBACK_BINLOG_DOWNLOAD_DIR = "flashback_binlog_download_dir";
    /**
     * 对于新加入的节点，本地磁盘文件为空，需要从OSS或Lindorm下载逻辑Binlog文件，此参数配置可以下载的最大个数
     */
    public static final String FLASHBACK_BINLOG_MAX_DOWNLOAD_FILE_COUNT = "flashback_binlog_max_download_file_count";
    /**
     * 从OSS下载Binlog文件的线程数
     */
    public static final String FLASHBACK_BINLOG_DOWNLOAD_THREAD_NUM = "flashback_binlog_download_thread_num";
    /**
     * SQL闪回结果文件下载链接有效时长，单位：秒
     */
    public static final String FLASHBACK_DOWNLOAD_LINK_PRESERVE_SECOND = "flashback_download_link_preserve_second";
    /**
     * SQL闪回上传接文件时，使用MultiUploadMode的阈值，单位：字节
     */
    public static final String FLASHBACK_UPLOAD_MULTI_MODE_THRESHOLD = "flashback_upload_multi_mode_threshold";

    //******************************************************************************************************************
    //**************************************************Binlog-X 相关的配置***********************************************
    //******************************************************************************************************************
    public static final String BINLOGX_ROCKSDB_BASE_PATH = "binlogx_rocksdb_base_path";
    public static final String BINLOGX_AUTO_INIT = "binlogx_auto_init";
    public static final String BINLOGX_DIR_PATH_PREFIX = "binlogx_dir_path_prefix";
    public static final String BINLOGX_STREAM_GROUP_NAME = "binlogx_stream_group_name";
    public static final String BINLOGX_STREAM_COUNT = "binlogx_stream_count";
    public static final String BINLOGX_WAIT_LATEST_TSO_TIMEOUT_SECOND = "binlogx_wait_latest_tso_timeout_second";
    public static final String BINLOGX_TRANSMIT_RELAY_ENGINE_TYPE = "binlogx_transmit_relay_engine_type";
    public static final String BINLOGX_TRANSMIT_RELAY_FILE_MAX_SIZE = "binlogx_transmit_relay_file_max_size";
    public static final String BINLOGX_TRANSMIT_READ_BATCH_ITEM_SIZE = "binlogx_transmit_read_batch_item_size";
    public static final String BINLOGX_TRANSMIT_READ_BATCH_BYTE_SIZE = "binlogx_transmit_read_batch_byte_size";
    public static final String BINLOGX_TRANSMIT_READ_FILE_BUFFER_SIZE = "binlogx_transmit_read_file_buffer_size";
    public static final String BINLOGX_TRANSMIT_READ_LOG_DETAIL_ENABLED = "binlogx_transmit_read_log_detail_enabled";
    public static final String BINLOGX_TRANSMIT_WRITE_BATCH_SIZE = "binlogx_transmit_write_batch_size";
    public static final String BINLOGX_TRANSMIT_WRITE_PARALLELISM = "binlogx_transmit_write_parallelism";
    public static final String BINLOGX_TRANSMIT_WRITE_QUEUE_SIZE = "binlogx_transmit_write_queue_size";
    public static final String BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_SIZE = "binlogx_transmit_write_file_buffer_size";
    public static final String BINLOGX_TRANSMIT_WRITE_FILE_BUFFER_USE_DIRECT_MEM =
        "binlogx_transmit_write_file_buffer_use_direct_mem";
    public static final String BINLOGX_TRANSMIT_WRITE_FILE_FLUSH_INTERVAL_MS =
        "binlogx_transmit_write_file_flush_interval_ms";
    public static final String BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD = "binlogx_transmit_write_slowdown_threshold";
    public static final String BINLOGX_TRANSMIT_WRITE_SLOWDOWN_SPEED = "binlogx_transmit_write_slowdown_speed";
    public static final String BINLOGX_TRANSMIT_WRITE_STOP_THRESHOLD = "binlogx_transmit_write_stop_threshold";
    public static final String BINLOGX_TRANSMIT_WRITE_LOG_DETAIL_ENABLED = "binlogx_transmit_write_log_detail_enabled";
    public static final String BINLOGX_TRANSMIT_HASH_LEVEL = "binlogx_transmit_hash_level";
    public static final String BINLOGX_RECORD_LEVEL_HASH_DB_LIST = "binlogx_record_level_hash_db_list";
    public static final String BINLOGX_RECORD_LEVEL_HASH_TABLE_LIST = "binlogx_record_level_hash_table_list";
    public static final String BINLOGX_DB_LEVEL_HASH_DB_LIST = "binlogx_db_level_hash_db_list";
    public static final String BINLOGX_DB_LEVEL_HASH_TABLE_LIST = "binlogx_db_level_hash_table_list";
    public static final String BINLOGX_TABLE_LEVEL_HASH_DB_LIST = "binlogx_table_level_hash_db_list";
    public static final String BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST = "binlogx_table_level_hash_table_list";
    public static final String BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST_REGEX =
        "binlogx_table_level_hash_table_list_regex";
    /**
     * 分配给Dispatcher内存，预留给rocksdb使用的内存大小
     */
    public static final String BINLOGX_SCHEDULE_DISPATCHER_ROCKSDB_RATIO = "binlogx_schedule_dispatcher_rocksdb_ratio";
    /**
     * 构建运行时拓扑时，每个container分配的Dispatcher的个数，如果count小于1，则按照memory模式进行构建
     */
    public static final String BINLOGX_SCHEDULE_DISPATCHER_COUNT_PER_NODE =
        "binlogx_schedule_dispatcher_count_per_node";
    /**
     * 构建运行时拓扑时，对Dispatcher进行内存分配的参考值
     * 1. 当Container可用内存大于Dispatcher最小可用内存，但小于memory unit的两倍时，该Container上只分配一个Dispatcher进程
     * 2. 当Container可用内存大于等于memory unit的两倍时，调度程序根据DN节点的数量情况，会考虑在该Container上拆分运行多个Task进程
     */
    public static final String BINLOGX_SCHEDULE_DISPATCHER_MEMORY_UNIT = "binlogx_schedule_dispatcher_memory_unit";
    /**
     * Dispatcher进程所需的最小内存，当Container进程所剩内存小于该值时，调度程序不会给该Container分配Dispatcher进程
     */
    public static final String BINLOGX_SCHEDULE_DISPATCHER_MEMORY_MIN = "binlogx_schedule_dispatcher_memory_min";
    /**
     * 是否支持清理旧版本的逻辑Binlog，默认true，一般测试环境排查问题时，可设置为false
     */
    public static final String BINLOGX_CLEAN_OLD_VERSION_BINLOG_ENABLED = "binlogx_clean_old_version_binlog_enabled";
    /**
     * 是否支持清理Dispatcher中的relay data, relay data是针对多流进行Hash后的log event，保存在rocksDb中，默认值：true
     */
    public static final String BINLOGX_CLEAN_RELAY_DATA_ENABLED = "binlogx_clean_relay_data_enabled";
    /**
     * 对relay data进行清理的频率，单位：分钟，默认值：1min
     */
    public static final String BINLOGX_CLEAN_RELAY_DATA_INTERVAL_MINUTE = "binlogx_clean_relay_data_interval_minute";
    /**
     * 多流场景下，所有Grpc client的flow control window size的总和，即每个client的 window-size = total-window-size / client-count
     * 如果不加控制的话，会导致直接内存out of memory
     */
    public static final String BINLOGX_TXN_STREAM_FLOW_CONTROL_WINDOW_MAX_SIZE =
        "binlogx_txn_stream_flow_control_window_max_size";
    /**
     * 多流场景下，所有流的seek buffer的总和的最大值
     */
    public static final String BINLOGX_FILE_SEEK_BUFFER_MAX_TOTAL_SIZE = "binlogx_file_seek_buffer_max_total_size";
    /**
     * kway多路归并，merge source queue size
     */
    public static final String BINLOGX_KWAY_SOURCE_QUEUE_SIZE = "binlogx_kway_source_queue_size";

    /**
     * logic TableMeta count
     */
    public static final String META_BUILD_FULL_SNAPSHOT_THRESHOLD = "meta_build_full_snapshot_threshold";

    /**
     * 构造元数据snapshot失败重试次数
     */
    public static final String META_BUILD_SNAPSHOT_RETRY_TIMES = "meta_build_snapshot_retry_times";

    //******************************************************************************************************************
    //*********************************************** Replica相关参数 ***************************************************
    //******************************************************************************************************************

    public static final String RPL_PERSIST_BASE_PATH = "rpl_persist_base_path";

    public static final String RPL_PERSIST_ENABLED = "rpl_persist_enabled";

    /**
     * rpl相关的任务，是否支持根据rplTask.gmt_modified探活
     */
    public static final String RPL_SUPPORT_RUNNING_CHECK = "rpl_task_support_running_check";

    public static final String RPL_SINGLE_TASK_MEMORY_WHEN_DISTRIBUTE = "rpl_single_task_memory_when_distribute";

    public static final String RPL_RANDOM_COMPARE_ALL = "rpl_random_compare_all";

    public static final String RPL_ROCKSDB_DESERIALIZE_PARALLELISM = "rpl_rocksdb_deserialize_parallelism";

    public static final String RPL_DEFAULT_IGNORE_DB_LIST = "rpl_default_ignore_db_list";

    public static final String RPL_DEFAULT_SQL_MODE = "rpl_default_sql_mode";

    public static final String RPL_POOL_CN_BLACK_IP_LIST = "rpl_pool_cn_black_ip_list";

    public static final String RPL_TASK_KEEP_ALIVE_INTERVAL_SECONDS = "rpl_task_keep_alive_interval_seconds";

    public static final String RPL_FILTER_TABLE_ERROR = "rpl_filter_table_error";

    public static final String RPL_PERSIST_SCHEMA_META_ENABLED = "rpl_persist_schema_meta_enabled";

    public static final String RPL_TABLE_META_MAX_CACHE_SIZE = "rpl_table_meta_max_cache_size";

    public static final String RPL_TABLE_META_EXPIRE_TIME_MINUTES = "rpl_table_meta_expire_time_minutes";

    public static final String RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND = "rpl_state_metrics_flush_interval_second";

    public static final String RPL_DDL_RETRY_INTERVAL_MILLS = "rpl_ddl_retry_interval_mills";

    public static final String RPL_DDL_RETRY_MAX_COUNT = "rpl_ddl_retry_max_count";

    public static final String RPL_DDL_WAIT_ALIGN_INTERVAL_MILLS = "rpl_ddl_wait_align_interval_mills";

    public static final String RPL_DDL_APPLY_COLUMNAR_ENABLED = "rpl_ddl_apply_columnar_enabled";

    public static final String RPL_DELAY_ALARM_THRESHOLD_SECOND = "rpl_delay_alarm_threshold_second";

    public static final String RPL_DDL_PARSE_ERROR_PROCESS_MODE = "rpl_ddl_parse_error_process_mode";

    public static final String RPL_ERROR_SQL_TRUNCATE_LENGTH = "rpl_error_sql_truncate_length";

    public static final String RPL_FULL_VALID_MIN_BATCH_ROWS_COUNT = "rpl_full_valid_min_batch_rows_count";

    public static final String RPL_FULL_VALID_MIN_BATCH_BYTE_SIZE = "rpl_full_valid_min_batch_byte_size";

    public static final String RPL_FULL_VALID_BATCH_SIZE = "rpl_full_valid_batch_size";

    public static final String RPL_FULL_VALID_MAX_SAMPLE_PERCENTAGE = "rpl_full_valid_max_sample_percentage";

    public static final String RPL_FULL_VALID_MAX_SAMPLE_ROWS_COUNT = "rpl_full_valid_max_sample_rows_count";

    public static final String RPL_FULL_VALID_TABLE_PARALLELISM = "rpl_full_valid_table_parallelism";

    public static final String RPL_REPAIR_PARALLELISM = "rpl_repair_parallelism";

    public static final String RPL_FULL_VALID_MAX_PERSIST_ROWS_COUNT = "rpl_full_valid_max_persist_rows_count";

    public static final String RPL_FULL_VALID_RECORDS_PER_SECOND = "rpl_full_valid_records_per_second";

    public static final String RPL_FULL_VALID_SKIP_COLLECT_STATISTIC = "rpl_full_valid_skip_collect_statistic";

    public static final String RPL_FULL_VALID_SAMPLE_COUNT = "rpl_full_valid_sample_count";

    public static final String RPL_FULL_VALID_AUTO_RESET_ERROR_TASKS = "rpl_full_valid_auto_reset_error_tasks";

    public static final String RPL_POLARDBX1_OLD_VERSION_OPTION = "rpl_polardbx1_old_version_option";

    public static final String RPL_SET_MAX_STATEMENT_TIME_OPTION = "rpl_set_max_statement_time_option";

    public static final String RPL_ASYNC_DDL_ENABLED = "rpl_async_ddl_enabled";

    public static final String RPL_ASYNC_DDL_THRESHOLD_IN_SECOND = "rpl_async_ddl_threshold_in_second";

    public static final String RPL_PARALLEL_SCHEMA_APPLY_ENABLED = "rpl_parallel_schema_apply_enabled";

    public static final String RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED = "rpl_parallel_schema_channel_enabled";

    public static final String RPL_PARALLEL_SCHEMA_CHANNEL_PARALLELISM = "rpl_parallel_schema_channel_parallelism";

    public static final String RPL_PARALLEL_TABLE_APPLY_ENABLED = "rpl_parallel_table_apply_enabled";

    public static final String RPL_PARALLEL_SCHEMA_APPLY_BATCH_SIZE = "rpl_parallel_schema_apply_batch_size";

    public static final String RPL_DEFAULT_MEMORY = "rpl_default_memory";

    public static final String RPL_CONNECTION_INIT_SQL = "rpl_connection_init_sql";

    public static final String RPL_APPLY_USE_CACHED_THREAD_POOL_ENABLED = "rpl_apply_use_cached_thread_pool_enabled";

    /**
     * 全量校验，表分批校验的临界行数
     */
    public static final String RPL_FULL_VALID_MAX_BATCH_ROWS_COUNT = "rpl_full_valid_max_batch_rows_count";

    /**
     * 全量校验，表分批校验的临界大小，单位：字节
     */
    public static final String RPL_FULL_VALID_MAX_BATCH_SIZE = "rpl_full_valid_max_batch_size";

    public static final String RPL_FULL_VALID_RUNNER_THREAD_POOL_CORE_SIZE =
        "rpl_full_valid_runner_thread_pool_core_size";
    public static final String RPL_FULL_VALID_RUNNER_THREAD_POOL_MAX_SIZE =
        "rpl_full_valid_runner_thread_pool_max_size";
    public static final String RPL_FULL_VALID_RUNNER_THREAD_POOL_KEEP_ALIVE_TIME_SECONDS =
        "rpl_full_valid_runner_thread_pool_keep_alive_time_seconds";
    public static final String RPL_FULL_VALID_RUNNER_THREAD_POOL_QUEUE_SIZE =
        "rpl_full_valid_runner_thread_pool_queue_size";
    public static final String RPL_FULL_VALID_CHECK_DETAIL_FETCH_SIZE = "rpl_full_valid_check_detail_fetch_size";

    public static final String RPL_FULL_VALID_CN_CONN_POOL_COUNT = "rpl_full_valid_cn_conn_pool_count";

    public static final String RPL_FULL_VALID_MOCK_SAMPLE = "rpl_full_valid_mock_sample";

    public static final String RPL_EXTRACTOR_DDL_LOG_OPEN = "rpl_extractor_ddl_log_open";

    public static final String RPL_LONG_SOCKET_TIMEOUT_MILLS = "rpl_long_socket_timeout_mills";

    public static final String RPL_SHORT_SOCKET_TIMEOUT_MILLS = "rpl_short_socket_timeout_mills";

    public static final String RPL_MERGE_SAME_RDS_TASK = "rpl_merge_same_rds_task";

    public static final String RPL_INC_BLACK_TABLE_LIST = "rpl_inc_black_table_list";

    public static final String RPL_INC_STOP_TIME_SECONDS = "rpl_inc_stop_time_seconds";

    public static final String RPL_INC_LOCAL_FILE_NUM = "rpl_inc_local_file_num";

    public static final String RPL_FULL_USE_IMPLICIT_ID = "rpl_full_use_implicit_id";

    public static final String RPL_COLS_UPDATE_MODE = "rpl_cols_update_mode";

    public static final String RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR =
        "rpl_inc_ddl_skip_miss_local_partition_error";

    public static final String RPL_MERGE_APPLY_GROUP_BY_TABLE_ENABLED = "rpl_merge_apply_group_by_table_enabled";

    public static final String RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED = "rpl_split_apply_in_transaction_enabled";

    public static final String RPL_APPLY_DRY_RUN_ENABLED = "rpl_apply_dry_run_enabled";

    public static final String RPL_INC_MAX_POOL_SIZE = "rpl_inc_max_pool_size";

    public static final String RPL_INC_MIN_POOL_SIZE = "rpl_inc_min_pool_size";


    /**
     * Columnar是否已经心跳超时的阈值
     */
    public static final String COLUMNAR_PROCESS_HEARTBEAT_TIMEOUT_MS =
        "columnar_process_heartbeat_timeout_ms";

    /**
     * Columnar是否已经延迟超时的阈值
     */
    public static final String COLUMNAR_PROCESS_LATENCY_TIMEOUT_MS =
        "columnar_process_latency_timeout_ms";

    /**
     * 实验室相关配置
     */
    public static final String TEST_OPEN_BINLOG_LAB_EVENT_SUPPORT = "test_open_binlog_lab_event_support";
    //******************************************************************************************************************
    //*********************************Binlog_System_Config表中有，但config文件中没有的一些配置******************************
    //******************************************************************************************************************

    public static final String EXPECTED_STORAGE_TSO_KEY =
        String.format("%s:expected_storage_tso",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_SNAPSHOT_VERSION_KEY =
        String.format("%s:cluster_snapshot_version",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));

    public static final String CLUSTER_TOPOLOGY_EXCLUDE_NODES = "cluster_topology_exclude_nodes";
    public static final String CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY =
        String.format("%s:%s", getPropertiesValue(ConfigKeys.CLUSTER_ID), CLUSTER_TOPOLOGY_EXCLUDE_NODES);
    public static final String CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY =
        String.format("%s:cluster_topology_dumper_master_node",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_SUSPEND_TOPOLOGY_REBUILDING =
        String.format("%s:cluster_suspend_topology_rebuilding",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String GLOBAL_BINLOG_LATEST_CURSOR =
        String.format("%s:global_latest_cursor", getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_EXECUTION_INSTRUCTION =
        String.format("%s:cluster_execution_instruction",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));
    public static final String CLUSTER_REBALANCE_INSTRUCTION =
        String.format("%s:cluster_rebalance_instruction",
            getPropertiesValue(ConfigKeys.CLUSTER_ID));
}
