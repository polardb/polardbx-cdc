# rpl_ddl之前一直没用,直接删除然后重建
DROP TABLE IF EXISTS `rpl_ddl`;
CREATE TABLE IF NOT EXISTS `rpl_ddl_main` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `ddl_tso` varchar(64) NOT NULL,
    `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `token` varchar(50) NOT NULL,
    `state` int(11) NOT NULL,
    `ddl_stmt` longtext,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fsm_ddl_tso` (`fsm_id`,`ddl_tso`),
    UNIQUE KEY `uk_token`(token),
    KEY `service_id` (`service_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `rpl_ddl_sub` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `ddl_tso` varchar(64) NOT NULL,
    `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    `state` int(11) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fsm_tso_task` (`fsm_id`,`ddl_tso`,`task_id`),
    KEY `service_id` (`service_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `rpl_stat_metrics` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `task_id` bigint(20) unsigned NOT NULL,
    `out_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `apply_count` bigint(20) unsigned NOT NULL DEFAULT '0',
    `in_eps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `out_bps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `in_bps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `out_insert_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `out_update_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `out_delete_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
    `receive_delay` bigint(20) unsigned NOT NULL DEFAULT '0',
    `process_delay` bigint(20) unsigned NOT NULL DEFAULT '0',
    `merge_batch_size` bigint(20) unsigned NOT NULL DEFAULT '0',
    `rt` bigint(20) unsigned NOT NULL DEFAULT '0',
    `skip_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
    `skip_exception_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
    `persist_msg_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
    `msg_cache_size` bigint(20) unsigned NOT NULL DEFAULT '0',
    `cpu_use_ratio` int(20) unsigned NOT NULL DEFAULT '0',
    `mem_use_ratio` int(20) unsigned NOT NULL DEFAULT '0',
    `full_gc_count` bigint(20) unsigned NOT NULL DEFAULT '0',
    `worker_ip` varchar(30) NOT NULL ,
    `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task` (`task_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

