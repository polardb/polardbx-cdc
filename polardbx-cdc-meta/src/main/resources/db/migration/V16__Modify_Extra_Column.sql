DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_extra_column_v16` $$
CREATE PROCEDURE modify_extra_column_v16()
BEGIN
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='extra')
    THEN
        alter table rpl_task modify column `extra` mediumtext DEFAULT NULL COMMENT '任务附加信息';
    END IF;
END $$
DELIMITER ;
CALL modify_extra_column_v16;