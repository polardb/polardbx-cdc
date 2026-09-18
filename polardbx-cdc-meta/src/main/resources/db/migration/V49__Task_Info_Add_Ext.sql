DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_task_info_v49` $$
CREATE PROCEDURE add_column_binlog_task_info_v49()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_task_info' AND column_name='ext')
	THEN
alter table binlog_task_info add column ext longtext COMMENT '当前版本支持的特性';
END IF;
END $$
DELIMITER ;
CALL add_column_binlog_task_info_v49;