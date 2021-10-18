DELIMITER $$

DROP PROCEDURE IF EXISTS `add_column_binlog_logic_meta_history` $$

CREATE PROCEDURE add_column_binlog_logic_meta_history()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND column_name='table_name')
	THEN
	    ALTER TABLE `binlog_logic_meta_history` ADD `table_name` VARCHAR(128)  NULL  DEFAULT NULL COMMENT '当前操作的表名'  AFTER `db_name`;
	END IF;
END $$
DELIMITER ;

CALL add_column_binlog_logic_meta_history;