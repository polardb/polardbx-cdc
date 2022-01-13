create TABLE IF NOT EXISTS `binlog_env_config_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `change_env_content` mediumtext NOT NULL,
  `instruction_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_tso` (`tso`),
  UNIQUE KEY `uindex_instructionId` (`instruction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
