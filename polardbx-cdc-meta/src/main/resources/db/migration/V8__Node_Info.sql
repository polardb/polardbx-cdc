DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_node_info_v8` $$
CREATE PROCEDURE add_column_binlog_node_info_v8()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='role')
	THEN
	    Alter table `binlog_node_info` add column `role` varchar(8) default '' COMMENT 'M:Master, S:Slaver';
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_node_info_v8;
