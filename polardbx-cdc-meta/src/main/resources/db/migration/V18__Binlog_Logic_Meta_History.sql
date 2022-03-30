DELIMITER $$

DROP PROCEDURE IF EXISTS `add_column_binlog_logic_meta_history_v18` $$

CREATE PROCEDURE add_column_binlog_logic_meta_history_v18()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND column_name='ddl_record_id')
	THEN
	    ALTER TABLE `binlog_logic_meta_history` ADD column `ddl_record_id` bigint  NULL  DEFAULT NULL COMMENT '打标记录对应的id';
	    ALTER TABLE `binlog_logic_meta_history` ADD index `idx_ddl_record_id`(`ddl_record_id`);
	END IF;

	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND column_name='ddl_job_id')
	THEN
	    ALTER TABLE `binlog_logic_meta_history` ADD column `ddl_job_id` bigint  NULL  DEFAULT NULL COMMENT '打标记录对应的job_id';
	END IF;
END $$
DELIMITER ;

CALL add_column_binlog_logic_meta_history_v18;