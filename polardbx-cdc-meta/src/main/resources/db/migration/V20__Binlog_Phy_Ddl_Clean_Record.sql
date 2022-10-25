CREATE TABLE IF NOT EXISTS binlog_phy_ddl_hist_clean_point (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `storage_inst_id` varchar(128) DEFAULT NULL,
  `ext` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_storage_inst_id` (`storage_inst_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;