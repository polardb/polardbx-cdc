CREATE TABLE IF NOT EXISTS binlog_node_info(
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `container_id` varchar(128) NOT NULL COMMENT '容器id',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `daemon_port` int(10) NOT NULL COMMENT 'daemon 守护端口，一台物理机可能部署多个容器，ip一样，需要port进一步区分',
  `available_ports` varchar(128) NOT NULL COMMENT '可用端口逗号(,)分隔',
  `status` int(10),
  `core` bigint(10) COMMENT 'CPU数',
  `mem` bigint(10) COMMENT '内存大小(MB)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS binlog_task_config (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `container_id` varchar(128) NOT NULL COMMENT '容器id',
  `task_name` varchar(64) NOT NULL COMMENT 'Dumper | Final_1 | Relay_1',
  `vcpu` int(10) COMMENT '虚拟cpu占比，目前无意义',
  `mem` int(10) COMMENT '分配内存值，单位mb',
  `ip` varchar(64) NOT NULL,
  `port` int(10) NOT NULL COMMENT '预分配的端口',
  `config` longtext NOT NULL COMMENT 'db黑名单 tb黑名单',
  `role` varchar(64) NOT NULL COMMENT 'Dumper | Final | Relay',
  `status` int(10) NOT NULL DEFAULT 0 COMMENT '1:自动调度开启，其他状态非0',
  PRIMARY KEY (`id`),
  CONSTRAINT `udx_cluster_task_name` UNIQUE (`cluster_id`,`task_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS binlog_task_info (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `task_name` varchar(64) NOT NULL COMMENT 'Final_1 | Relay_1',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `port` int(10) NOT NULL,
  `role` varchar(8) COMMENT 'Final | Relay',
  `status` int(10) NOT NULL DEFAULT 0 COMMENT '0:提供服务，其他状态非0',
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `udx_cluster_ip_port` UNIQUE (`cluster_id`,`ip`,`port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS binlog_dumper_info (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `task_name` varchar(64) NOT NULL COMMENT 'Dumper',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `port` int(10) NOT NULL,
  `role` varchar(8) COMMENT 'M:Master, S:Slaver',
  `status` int(10) NOT NULL DEFAULT 0 COMMENT '0:提供服务，其他状态非0',
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `udx_cluster_ip_port` UNIQUE (`cluster_id`,`ip`,`port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;