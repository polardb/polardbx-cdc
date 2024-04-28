CREATE TABLE IF NOT EXISTS rpl_full_valid_task
(
    id                      bigint(20) unsigned                  not null auto_increment,
    state_machine_id        bigint(20) unsigned                  not null comment '状态机id',
    src_logical_db          varchar(128)                         null comment '源端逻辑库名',
    src_logical_table       varchar(128)                         null comment '源端逻辑表名',
    dst_logical_db          varchar(128)                         null comment '目标端逻辑库名',
    dst_logical_table       varchar(128)                         null comment '目标端表名',
    task_stage              varchar(128)                         not null comment '任务阶段, INIT/CHECK/REPAIR',
    task_state              varchar(128)                         not null comment '任务状态, RUNNING/PAUSED/FINISHED',
    create_time             datetime   default CURRENT_TIMESTAMP not null,
    update_time             datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key(`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS rpl_full_valid_sub_task
(
    id                      bigint(20) unsigned                  not null auto_increment,
    state_machine_id        bigint(20) unsigned                  not null comment '状态机id',
    task_id                 bigint(20) unsigned                  not null comment '全量校验任务id',
    task_stage              varchar(128)                         not null comment '任务所属阶段',
    task_state              varchar(128)                         not null comment '任务状态',
    task_type               varchar(128)                         not null comment '任务类型',
    task_config             longtext                             null comment '任务配置',
    summary                 longtext                             null comment '任务摘要',
    create_time             datetime   default CURRENT_TIMESTAMP not null,
    update_time             datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key(`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS rpl_full_valid_diff
(
    id                      bigint(20) unsigned                  not null auto_increment,
    task_id                 bigint(20) unsigned                  not null comment '全量校验任务id',
    src_logical_db          varchar(128)                         null comment '源端逻辑库名',
    src_logical_table       varchar(128)                         null comment '源端逻辑表名',
    dst_logical_db          varchar(128)                         null comment '目标端逻辑库名',
    dst_logical_table       varchar(128)                         null comment '目标端表名',
    src_key_name            varchar(128)                         null comment '源端pk/uk列名/schema类别',
    src_key_val             varchar(256)                         null comment '源端pk/uk值/schema值',
    dst_key_name            varchar(128)                         null comment '目标端pk/uk列名/schema类别',
    dst_key_val             varchar(256)                         null comment '目标端pk/uk值/schema值',
    error_type              varchar(128)                         not null comment '错误类型, DIFF/MISS/ORPHAN/SCHEMASRC/SCHEMADST',
    status                  varchar(128)                         not null default 'FOUND' comment '状态, FOUND/REPAIRED',
    create_time             datetime   default CURRENT_TIMESTAMP not null,
    update_time             datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key(`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;
