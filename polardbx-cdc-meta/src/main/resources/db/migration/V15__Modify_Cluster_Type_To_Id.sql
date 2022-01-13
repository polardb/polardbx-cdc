DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_column_cluster_type_to_id_and_add_context_v14` $$
CREATE PROCEDURE modify_column_cluster_type_to_id_and_add_context_v14()
BEGIN
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='cluster_type')
    THEN
        alter table rpl_task drop column `cluster_type`;
    END IF;

	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='cluster_id')
    THEN
        alter table rpl_task add `cluster_id` varchar(64) DEFAULT NULL COMMENT '任务运行集群的id';
    END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_state_machine' AND column_name='cluster_id')
    THEN
        alter table rpl_state_machine add column cluster_id varchar(64) default NULL COMMENT '任务运行集群的id';
    END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_state_machine' AND column_name='context')
    THEN
        alter table rpl_state_machine add column context mediumtext default NULL COMMENT '状态机上下文，例如用于保存状态机运行的结果等';
    END IF;

    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_state_machine' AND column_name='config')
    THEN
        alter table rpl_state_machine modify column `config` mediumtext DEFAULT NULL COMMENT '状态机元数据';
    END IF;

    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_ddl' AND column_name='ddl_stmt')
    THEN
        alter table rpl_ddl modify column `ddl_stmt` mediumtext DEFAULT NULL COMMENT '状态机元数据';
    END IF;

END $$
DELIMITER ;
CALL modify_column_cluster_type_to_id_and_add_context_v14;