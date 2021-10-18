DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_task_config_v7` $$
CREATE PROCEDURE add_column_binlog_task_config_v7()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_task_config' AND column_name='version')
	THEN
	    Alter table `binlog_task_config` add column `version` bigint(20) not null  default 1;
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_task_config_v7;


DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_dumper_info_v7` $$
CREATE PROCEDURE add_column_binlog_dumper_info_v7()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_dumper_info' AND column_name='container_id')
	THEN
	    Alter table `binlog_dumper_info` add column `container_id` varchar(128)  not null default '';
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_dumper_info' AND column_name='version')
	THEN
	    Alter table `binlog_dumper_info` add column `version` bigint(20) not null  default 0;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name='binlog_dumper_info' AND constraint_name='udx_taskname')
	THEN
	    Alter table `binlog_dumper_info` add unique key `udx_taskname`(`cluster_id`,`task_name`);
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_dumper_info_v7;


DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_task_info_v7` $$
CREATE PROCEDURE add_column_binlog_task_info_v7()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_task_info' AND column_name='container_id')
	THEN
	    Alter table `binlog_task_info` add column `container_id` varchar(128) not null default '';
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_task_info' AND column_name='version')
	THEN
	    Alter table `binlog_task_info` add column `version` bigint(20) not null  default 0;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name='binlog_task_info' AND constraint_name='udx_taskname')
	THEN
	    Alter table `binlog_task_info` add unique key `udx_taskname`(`cluster_id`,`task_name`);
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_task_info_v7;


DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_node_info_v7` $$
CREATE PROCEDURE add_column_binlog_node_info_v7()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='gmt_heartbeat')
	THEN
	    Alter table `binlog_node_info` add column `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='latest_cursor')
	THEN
	    Alter table `binlog_node_info` add column `latest_cursor` varchar(200) not null default '';
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND constraint_name='udx_container_id')
	THEN
	    Alter table `binlog_node_info` add unique key `udx_container_id`(`container_id`);
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_node_info_v7;


CREATE TABLE IF NOT EXISTS `binlog_schedule_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` bigint(20) NOT NULL,
  `content` mediumtext NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_key` (`version`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;