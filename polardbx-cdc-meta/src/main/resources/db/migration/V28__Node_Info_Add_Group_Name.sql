DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_node_info_v25` $$
CREATE PROCEDURE add_column_binlog_node_info_v25()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='group_name')
	THEN
	    Alter table `binlog_node_info` add column `group_name` varchar(200) DEFAULT NULL COMMENT '所属分组';
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_node_info_v25;