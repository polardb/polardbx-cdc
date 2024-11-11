DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_column_columnar_task_v34` $$
CREATE PROCEDURE modify_column_columnar_task_v34()
BEGIN
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='columnar_task' AND column_name='gmt_modified')
	THEN
        alter table columnar_task modify column `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
    END IF;
END $$
DELIMITER ;

call modify_column_columnar_task_v34;