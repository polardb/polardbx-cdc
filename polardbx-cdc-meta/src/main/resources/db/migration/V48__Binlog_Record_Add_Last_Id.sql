DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_oss_record_v48` $$
CREATE PROCEDURE add_column_binlog_oss_record_v48()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='last_xid')
	THEN
alter table binlog_oss_record add column last_xid bigint(20) default NULL;
END IF;
END $$
DELIMITER ;
CALL add_column_binlog_oss_record_v48;
