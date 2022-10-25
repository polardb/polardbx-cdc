CREATE TABLE IF NOT EXISTS `rpl_task_config` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `task_id` bigint(20) unsigned NOT NULL,
    `extractor_config` longtext,
    `pipeline_config` longtext,
    `applier_config` longtext,
    `memory` int(10) NOT NULL DEFAULT '1536',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


DELIMITER $$
DROP PROCEDURE IF EXISTS `remove_uk_validation_task_v21` $$
CREATE PROCEDURE remove_uk_validation_task_v21()
BEGIN
	IF EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name='validation_task' AND constraint_name='validation_task_external_id_uindex')
	THEN
        ALTER TABLE `validation_task` DROP INDEX validation_task_external_id_uindex;
    END IF;
END $$
DELIMITER ;
CALL remove_uk_validation_task_v21;