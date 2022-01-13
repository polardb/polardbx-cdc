CREATE TABLE IF NOT EXISTS `rpl_task` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status` int(10) NOT NULL,
    `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `type` int(10) NOT NULL DEFAULT '0',
    `master_host` varchar(256) DEFAULT NULL,
    `master_port` int(11) DEFAULT '-1',
    `extractor_config` text,
    `pipeline_config` text,
    `applier_config` text,
    `position` varchar(256) NOT NULL,
    `last_error` text,
    `statistic` text,
    `extra` text,
    `worker` varchar(20) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY (`service_id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `rpl_service` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `service_type` int(10) NOT NULL,
    `state_list` varchar(256) NOT NULL,
    `channel` varchar(128) DEFAULT NULL,
    `status` int(10) NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel` (`channel`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `rpl_ddl` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `ddl_tso` varchar(64) NOT NULL,
    `job_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `state` int(11) NOT NULL,
    `ddl_stmt` longtext,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ddl_tso` (`ddl_tso`),
    KEY `service_id` (`service_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `rpl_table_position` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `full_table_name` varchar(64) NOT NULL,
    `position` varchar(256) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_table_position` (`task_id`, `full_table_name`),
    KEY `task_id` (`task_id`),
    KEY `service_id` (`service_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS`rpl_db_full_position` (
                                        `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
                                        `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
                                        `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
                                        `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
                                        `full_table_name` varchar(64) NOT NULL,
                                        `total_count` bigint(20) unsigned NOT NULL DEFAULT '0',
                                        `finished_count` bigint(20) unsigned NOT NULL DEFAULT '0',
                                        `finished` int(10) unsigned NOT NULL DEFAULT '0',
                                        `position` varchar(256) DEFAULT NULL,
                                        `end_position` varchar(256) DEFAULT NULL,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_full_position` (`task_id`,`full_table_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS`rpl_state_machine` (
                                     `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
                                     `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     `type` int(10) NOT NULL DEFAULT '0',
                                     `class_name` varchar(256) NOT NULL,
                                     `channel` varchar(128) DEFAULT NULL,
                                     `status` int(10) NOT NULL DEFAULT '0',
                                     `state` int(10) NOT NULL DEFAULT '0',
                                     `config` longtext NOT NULL,
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_channel` (`channel`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

-- auto-generated definition
create table if not exists validation_task
(
    id                  bigint auto_increment
        primary key,
    external_id         varchar(64)                          not null,
    state_machine_id    varchar(64)                          not null comment '评估迁移任务 id',
    service_id          varchar(64)                          not null comment '评估迁移任务各逻辑步骤 id, 如迁移任务对应 1 个全量同步任务, 1 个增量同步任务等等',
    task_id             varchar(64)                          not null comment '分配至当前容器的任务步骤 id. 物理概念',
    type                int                                  not null comment '校验任务类型. 1 : 全量校验, 2 : 增量校验',
    state               int                                  not null comment '当前任务运行状态 ',
    drds_ins_id         varchar(64)                          null comment '1.0 DRDS 实例 id',
    rds_ins_id          varchar(64)                          null comment '1.0 RDS 实例 id',
    src_logical_db      varchar(64)                          null comment '逻辑库名',
    src_logical_table   varchar(128)                         null comment '逻辑表名',
    src_logical_key_col varchar(128)                         null comment 'pk/uk',
    src_phy_db          varchar(64)                          null comment '物理库名',
    src_phy_table       varchar(128)                         null comment '物理表名',
    src_phy_key_col     varchar(128)                         null comment 'physical pk/uk',
    polardbx_ins_id     varchar(64)                          null comment '2.0 实例 id',
    dst_logical_db      varchar(64)                          null comment '2.0 逻辑库名',
    dst_logical_table   varchar(128)                         null comment '2.0 逻辑表名',
    dst_logical_key_col varchar(128)                         null comment 'target pk/uk',
    config              text                                 null comment '任务配置 json',
    stats               text                                 null comment '物理表任务统计信息. 如已校验行数, 失败行数等等',
    task_range          varchar(1024)                        null comment '全量校验范围',
    deleted             tinyint(1) default 0                 null,
    create_time         datetime   default CURRENT_TIMESTAMP not null,
    update_time         datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint validation_task_external_id_uindex unique (external_id),
    index validation_task_src_index (state_machine_id, src_phy_db)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

-- auto-generated definition
CREATE TABLE IF NOT EXISTS validation_diff
(
    id                  bigint auto_increment
        primary key,
    state_machine_id    varchar(64)                          not null,
    service_id          varchar(64)                          not null,
    task_id             varchar(64)                          not null,
    validation_task_id  varchar(64)                          not null comment '校验任务 external id',
    type                int                                  not null comment '全量校验, 增量校验',
    state               int                                  not null comment '新创建, 复检等等',
    diff                text                                 null comment 'diff detail json',
    src_logical_db      varchar(128)                         null comment '源端逻辑库名',
    src_logical_table   varchar(128)                         null comment '源端逻辑表名',
    src_logical_key_col varchar(128)                         null,
    src_phy_db          varchar(128)                         null comment '源端物理库名',
    src_phy_table       varchar(128)                         null comment '源端物理表名',
    src_phy_key_col     varchar(128)                         null comment '源端 pk/uk',
    src_key_col_val     varchar(256)                         not null comment '源端 pk/uk 值',
    dst_logical_db      varchar(128)                         null comment '目标端逻辑库名',
    dst_logical_table   varchar(128)                         null comment '目标端表名',
    dst_logical_key_col varchar(128)                         null comment '目标端逻辑 pk/uk',
    dst_key_col_val     varchar(256)                         not null comment '目标端逻辑 pk/uk 值',
    deleted             tinyint(1) default 0                 not null,
    create_time         datetime   default CURRENT_TIMESTAMP not null,
    update_time         datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    index validation_diff_srcdb_index (state_machine_id, src_phy_db, src_phy_table)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;
