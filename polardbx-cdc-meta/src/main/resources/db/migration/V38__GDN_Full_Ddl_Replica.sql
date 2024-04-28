DELIMITER $$
drop procedure IF EXISTS `redefineColumn34` $$
create procedure redefineColumn34(IN tableName varchar(60), in columnName varchar(20), in ddlAction varchar(10), in typeAndCommentPrefix varchar(300))
BEGIN
    DECLARE V_SQL VARCHAR(500);
    set @V_SQL = '';
    IF ddlAction = 'ADD' THEN
        IF NOT EXISTS(select * from information_schema.columns where table_schema=(select database()) and table_name=tableName and column_name=columnName)
        THEN
            set V_SQL = CONCAT('alter table `',tableName, '` add column `',columnName,'` ', typeAndCommentPrefix);
            set @V_SQL=V_SQL;
        END IF;
    END IF;

    IF ddlAction = 'DROP'
    THEN
        IF EXISTS(select * from information_schema.columns where table_schema=(select database()) and table_name=tableName and column_name=columnName)
        THEN
            set V_SQL = CONCAT('alter table `',tableName, '` drop column `',columnName,'`');
            set @V_SQL=V_SQL;
        END IF;
    END IF;

    IF ddlAction = 'MODIFY'
    THEN
        IF EXISTS(select * from information_schema.columns where table_schema=(select database()) and table_name=tableName and column_name=columnName)
        THEN
            set V_SQL = CONCAT('alter table `',tableName, '` modify column `',columnName, '` ', typeAndCommentPrefix);
            set @V_SQL=V_SQL;
        END IF;
    END IF;
    IF @v_sql!='' THEN
        PREPARE stmt FROM @v_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

call redefineColumn34('binlog_logic_meta_history', 'need_apply', 'ADD', 'tinyint(1) default 1 not null');
call redefineColumn34('rpl_ddl_main','async_flag','ADD','tinyint(1) default 0 not null');
call redefineColumn34('rpl_ddl_main','async_state','ADD','varchar(32) NOT NULL');
call redefineColumn34('rpl_ddl_main','schema_name','ADD','varchar(100) default null');
call redefineColumn34('rpl_ddl_main','table_name','ADD','varchar(100) default null');
call redefineColumn34('rpl_ddl_main','task_id','ADD','bigint(20) unsigned default 0 NOT NULL');
call redefineColumn34('rpl_ddl_sub','schema_name','ADD','varchar(100) default null');
call redefineColumn34('rpl_ddl_main','parallel_seq','ADD','int not null default 0');
call redefineColumn34('rpl_ddl_sub','parallel_seq','ADD','int not null default 0');

DELIMITER $$
drop procedure IF EXISTS `add_idx_4_rpl_ddl_main` $$
create procedure add_idx_4_rpl_ddl_main()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_main' AND INDEX_NAME='idx_async')
	THEN
        alter table `rpl_ddl_main` add index `idx_async`(`async_flag`,`async_state`);
    END IF;
END $$
DELIMITER ;

call add_idx_4_rpl_ddl_main;

DELIMITER $$
drop procedure IF EXISTS `add_idx_5_rpl_ddl_main` $$
create procedure add_idx_5_rpl_ddl_main()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_main' AND INDEX_NAME='idx_schema_table')
	THEN
        alter table `rpl_ddl_main` add index `idx_schema_table`(`schema_name`,`table_name`);
    END IF;
END $$
DELIMITER ;

call add_idx_5_rpl_ddl_main;

DELIMITER $$
drop procedure IF EXISTS `add_idx_6_rpl_ddl_main` $$
create procedure add_idx_6_rpl_ddl_main()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_main' AND INDEX_NAME='idx_fsm_task')
	THEN
        alter table `rpl_ddl_main` add index `idx_fsm_task` (`fsm_id`,`task_id`);
    END IF;
END $$
DELIMITER ;

call add_idx_6_rpl_ddl_main;

DELIMITER $$
drop procedure IF EXISTS `drop_add_idx_1_rpl_ddl_sub` $$
create procedure drop_add_idx_1_rpl_ddl_sub()
BEGIN
    IF EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_sub' AND INDEX_NAME='uk_fsm_tso_task')
	THEN
        alter table `rpl_ddl_sub` drop index `uk_fsm_tso_task`;
    END IF;
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_sub' AND INDEX_NAME='uk_fsm_task_tso')
	THEN
        alter table `rpl_ddl_sub` add CONSTRAINT `uk_fsm_task_tso` unique (`fsm_id`,`task_id`,`ddl_tso`);
    END IF;

    IF EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_sub' AND INDEX_NAME='service_id')
	THEN
        alter table `rpl_ddl_sub` drop index `service_id`;
    END IF;
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='rpl_ddl_sub' AND INDEX_NAME='fsm_service_tso')
	THEN
        alter table `rpl_ddl_sub` add  index `fsm_service_tso` (`fsm_id`,`service_id`,`ddl_tso`);
    END IF;
END $$
DELIMITER ;

call drop_add_idx_1_rpl_ddl_sub;
