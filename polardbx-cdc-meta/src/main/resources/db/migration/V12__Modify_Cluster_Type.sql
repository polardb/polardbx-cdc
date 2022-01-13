DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_column_cluster_type_v12` $$
CREATE PROCEDURE modify_column_cluster_type_v12()
BEGIN
    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_node_info' AND column_name='cluster_type')
	THEN
        alter table binlog_node_info modify column `cluster_type` varchar(32) default NULL;
    END IF;
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='cluster_type')
    THEN
        alter table rpl_task modify column `cluster_type` varchar(32) DEFAULT NULL COMMENT '任务类型';
    END IF;
END $$
DELIMITER ;
CALL modify_column_cluster_type_v12;