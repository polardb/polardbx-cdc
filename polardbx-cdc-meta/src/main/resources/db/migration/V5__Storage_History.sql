create TABLE IF NOT EXISTS `binlog_storage_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `storage_content` mediumtext NOT NULL,
  `status` int(10) NOT NULL,
  `instruction_id` varchar(128)  NUll,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_tso` (`tso`),
  UNIQUE KEY `uindex_instructionId` (`instruction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

create TABLE IF NOT EXISTS `binlog_system_config` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `config_key` varchar(128) NOT NULL,
  `config_value` mediumtext NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
