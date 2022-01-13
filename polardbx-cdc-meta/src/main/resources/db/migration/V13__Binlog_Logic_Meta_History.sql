DELIMITER $$

DROP PROCEDURE IF EXISTS `add_column_binlog_logic_meta_history` $$

CREATE PROCEDURE add_column_binlog_logic_meta_history()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND column_name='sql_kind')
	THEN
	    ALTER TABLE `binlog_logic_meta_history` ADD `sql_kind` VARCHAR(128)  NULL  DEFAULT NULL COMMENT 'DDL类型'  AFTER `table_name`;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND column_name='ext_info')
    THEN
        ALTER TABLE `binlog_logic_meta_history` ADD `ext_info` MEDIUMTEXT COMMENT '扩展信息'  AFTER `type`;
    END IF;
END $$
DELIMITER ;

CALL add_column_binlog_logic_meta_history;