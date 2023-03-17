create TABLE IF NOT EXISTS `binlog_storage_sequence` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `storage_inst_id` varchar(128) NOT NULL,
  `storage_seq` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_storage_inst_id` (`storage_inst_id`),
  UNIQUE KEY `uindex_storage_seq` (`storage_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;