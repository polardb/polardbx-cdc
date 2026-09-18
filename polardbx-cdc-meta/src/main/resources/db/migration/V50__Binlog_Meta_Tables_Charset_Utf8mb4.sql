DELIMITER $$
DROP PROCEDURE IF EXISTS `convert_charset_utf8mb4_v50` $$
CREATE PROCEDURE convert_charset_utf8mb4_v50()
BEGIN
    IF EXISTS(SELECT * FROM information_schema.tables WHERE table_schema=(select database()) AND table_name='binlog_phy_ddl_history' AND table_collation != 'utf8mb4_general_ci')
	THEN
alter table `binlog_phy_ddl_history` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
END IF;
    IF EXISTS(SELECT * FROM information_schema.tables WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND table_collation != 'utf8mb4_general_ci')
	THEN
alter table `binlog_logic_meta_history` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
END IF;
END $$
DELIMITER ;
CALL convert_charset_utf8mb4_v50;
