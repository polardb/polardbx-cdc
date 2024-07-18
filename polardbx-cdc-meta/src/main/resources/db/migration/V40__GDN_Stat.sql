DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_gdn_stat_v40` $$
CREATE PROCEDURE add_column_gdn_stat_v40()
BEGIN
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='rpl_stat_metrics' AND column_name='true_delay_mills')
	THEN
        alter table rpl_stat_metrics add column `true_delay_mills` bigint unsigned NOT NULL DEFAULT '0';
    END IF;
END $$
DELIMITER ;

call add_column_gdn_stat_v40;