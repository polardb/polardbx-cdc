CREATE TABLE IF NOT EXISTS `columnar_node_info` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
    `container_id` varchar(64) NOT NULL COMMENT '容器id',
    `ip` varchar(20) NOT NULL DEFAULT '',
    `daemon_port` int(10) NOT NULL COMMENT 'daemon 守护端口，一台物理机可能部署多个容器，ip一样，需要port进一步区分',
    `available_ports` varchar(128) NOT NULL COMMENT '可用端口逗号(,)分隔',
    `status` int(10) DEFAULT NULL,
    `core` bigint(10) DEFAULT NULL COMMENT 'CPU数',
    `mem` bigint(10) DEFAULT NULL COMMENT '内存大小(MB)',
    `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `latest_cursor` varchar(600) NOT NULL DEFAULT '',
    `role` varchar(8) DEFAULT '' COMMENT 'M:Master, S:Slaver',
    `cluster_type` varchar(32) DEFAULT NULL,
    `group_name` varchar(200) DEFAULT NULL COMMENT '所属分组',
    PRIMARY KEY (`id`),
    UNIQUE KEY `udx_container_id` (`container_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `columnar_task_config`(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created`  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `cluster_id`   varchar(64) NOT NULL COMMENT '所属集群',
    `container_id` varchar(64) NOT NULL COMMENT '容器id',
    `task_name`    varchar(64) NOT NULL COMMENT 'Columnar',
    `vcpu`         int(10) DEFAULT NULL COMMENT '虚拟cpu占比，目前无意义',
    `mem`          int(10) DEFAULT NULL COMMENT '分配内存值，单位mb',
    `ip`           varchar(64) NOT NULL,
    `port`         int(10) NOT NULL COMMENT '预分配的端口',
    `config`       longtext    NOT NULL COMMENT 'db黑名单 tb黑名单',
    `role`         varchar(64) NOT NULL COMMENT 'Leader | Standby ',
    `status`       int(10) NOT NULL DEFAULT '0' COMMENT '1:自动调度开启，其他状态非0',
    `version`      bigint(20) NOT NULL DEFAULT '1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `udx_cluster_task_name` (`cluster_id`,`task_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `columnar_task`(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created`   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `cluster_id`    varchar(64) NOT NULL COMMENT '所属集群',
    `task_name`     varchar(64) NOT NULL COMMENT 'Columnar',
    `ip`            varchar(20) NOT NULL DEFAULT '',
    `port`          int(10) NOT NULL,
    `role`          varchar(8) COMMENT 'M:Master, S:Slaver',
    `status`        int(10) NOT NULL DEFAULT 0 COMMENT '0:提供服务，其他状态非0',
    `gmt_heartbeat` timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `container_id`  varchar(50) NOT NULL DEFAULT '',
    `version`       bigint(20) NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `udx_cluster_ip_port` (`cluster_id`,`ip`,`port`),
    UNIQUE KEY `udx_taskname` (`cluster_id`,`task_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;