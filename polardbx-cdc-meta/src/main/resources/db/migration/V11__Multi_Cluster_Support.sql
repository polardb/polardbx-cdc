DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_node_info_v11` $$
CREATE PROCEDURE add_column_binlog_node_info_v11()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='cluster_type')
	THEN
	    alter table binlog_node_info add column cluster_type int(11) default NULL;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='cluster_type')
	THEN
	    alter table rpl_task add column `cluster_type` int(11) DEFAULT NULL COMMENT '任务类型';
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_node_info_v11;
