CREATE TABLE IF NOT EXISTS binlog_logic_meta_history (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `db_name` varchar(128) NOT NULL DEFAULT '' COMMENT '引擎db_info表是128',
  `ddl` mediumtext NOT NULL,
  `topology` mediumtext,
  `type` tinyint(11) NOT NULL COMMENT '1:快照，2：DDL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_tso_db` (`tso`,`db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS binlog_phy_ddl_history (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `binlog_file` varchar(32) DEFAULT NULL,
  `pos` int(11) DEFAULT NULL,
  `storage_inst_id` varchar(128) DEFAULT NULL,
  `db_name` varchar(128) NOT NULL DEFAULT '' COMMENT '引擎db_info表是128',
  `ddl` mediumtext NOT NULL,
  `extra` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_tso_db` (`tso`,`db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;