DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_import_meta_v19` $$
CREATE PROCEDURE modify_import_meta_v19()
BEGIN
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='extractor_config')
    THEN
        alter table rpl_task modify column `extractor_config` longtext;
    END IF;
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='pipeline_config')
    THEN
        alter table rpl_task modify column `pipeline_config` longtext;
    END IF;
	IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='applier_config')
    THEN
        alter table rpl_task modify column `applier_config` longtext;
    END IF;
    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_task' AND column_name='worker')
    THEN
        alter table rpl_task modify column `worker` varchar(64) DEFAULT NULL;
    END IF;
    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_table_position' AND column_name='full_table_name')
    THEN
        alter table rpl_table_position modify column `full_table_name` varchar(128) DEFAULT NULL;
    END IF;
    IF EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_db_full_position' AND column_name='full_table_name')
    THEN
        alter table rpl_db_full_position modify column `full_table_name` varchar(128) DEFAULT NULL;
    END IF;
END $$
DELIMITER ;
CALL modify_import_meta_v19;