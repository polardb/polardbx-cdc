create TABLE IF NOT EXISTS `binlog_semi_snapshot` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `storage_inst_id` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_storage_tso` (`storage_inst_id`,`tso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;