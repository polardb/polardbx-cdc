create TABLE IF NOT EXISTS `binlog_oss_record` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `binlog_file` varchar(32) NOT NULL,
  `upload_status` int(11) DEFAULT '0',
  `purge_status` int(11) DEFAULT '0',
  `upload_host` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `binlog_oss_record_binlog_file_uindex` (`binlog_file`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8
