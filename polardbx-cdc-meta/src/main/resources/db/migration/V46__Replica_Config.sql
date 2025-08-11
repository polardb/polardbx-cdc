DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_state_machine_config_v46` $$
CREATE PROCEDURE modify_state_machine_config_v46()
BEGIN
    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_state_machine' AND column_name='config')
	THEN
        alter table rpl_state_machine modify column `config` longtext COMMENT '状态机元数据';
    END IF;
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='validation_diff' AND INDEX_NAME='validation_diff_index')
	THEN
        alter table validation_diff add index `validation_diff_index` (`state_machine_id`,`src_logical_db`,`src_logical_table`,`state`,`deleted`);
    END IF;
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='validation_task' AND INDEX_NAME='validation_task_index')
	THEN
        alter table validation_task add index `validation_task_index` (`state_machine_id`,`src_logical_db`,`src_logical_table`);
    END IF;
END $$
DELIMITER ;
CALL modify_state_machine_config_v46;