DELIMITER $$
DROP PROCEDURE IF EXISTS `modify_uinque_idx` $$
CREATE PROCEDURE modify_uinque_idx(IN tableName varchar(60), IN idxName varchar(20), IN sqlSuffix varchar(200))
BEGIN
    DECLARE V_SQL VARCHAR(500);
	IF EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name=tableName AND constraint_name=idxName)
	THEN
        set @V_SQL = CONCAT('alter table `',tableName, '` drop key `',idxName, '` ');
        PREPARE stmt FROM @v_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
	END IF;

	IF NOT EXISTS(SELECT * FROM information_schema.table_constraints WHERE table_schema=(select database()) AND table_name=tableName AND constraint_name=idxName)
	THEN
        set @V_SQL = CONCAT('alter table `',tableName, '` add UNIQUE KEY `',idxName,'` ',sqlSuffix);
        PREPARE stmt FROM @v_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
	END IF;
END $$
DELIMITER ;

DELIMITER $$
DROP PROCEDURE IF EXISTS `add_idx_db_name_4_binlog_phy_ddl_history` $$
CREATE PROCEDURE add_idx_db_name_4_binlog_phy_ddl_history()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_logic_meta_history' AND INDEX_NAME='idx_logic_db_name')
	THEN
	    ALTER TABLE `binlog_logic_meta_history` ADD index `idx_logic_db_name`(`db_name`);
	END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_phy_ddl_history' AND INDEX_NAME='idx_phy_db_name')
	THEN
	    ALTER TABLE `binlog_phy_ddl_history` ADD index `idx_phy_db_name`(`db_name`);
	END IF;
END $$
DELIMITER ;

call modify_uinque_idx('binlog_phy_ddl_history','udx_tso_db','(`cluster_id`,`storage_inst_id`,`tso`)');
call add_idx_db_name_4_binlog_phy_ddl_history;