DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_oss_record_v23` $$
CREATE PROCEDURE add_column_binlog_oss_record_v23()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='last_tso')
	THEN
	    alter table binlog_oss_record add column `last_tso` varchar(128) default null comment 'binlog文件最大tso';
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_oss_record_v23;