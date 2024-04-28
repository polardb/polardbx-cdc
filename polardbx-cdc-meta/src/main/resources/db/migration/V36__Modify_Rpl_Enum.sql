alter table rpl_task
    modify column `status` varchar(32) not null;
alter table rpl_task
    modify column `type` varchar(32) not null;
alter table rpl_service
    modify column `status` varchar(32) not null;
alter table rpl_service
    modify column `service_type` varchar(32) not null;
alter table rpl_state_machine
    modify column `status` varchar(32) not null;
alter table rpl_state_machine
    modify column `type` varchar(32) not null;
alter table rpl_state_machine
    modify column `state` varchar(32) not null;
alter table validation_task
    modify column `state` varchar(32) not null;
alter table validation_task
    modify column `type` varchar(32) not null;
alter table validation_diff
    modify column `state` varchar(32) not null;
alter table validation_diff
    modify column `type` varchar(32) not null;
alter table rpl_ddl_main
    modify column `state` varchar(32) not null;
alter table rpl_ddl_sub
    modify column `state` varchar(32) not null;

UPDATE rpl_task
SET status =
        CASE status
            WHEN 10 THEN 'READY'
            WHEN 20 THEN 'RUNNING'
            WHEN 30 THEN 'STOPPED'
            WHEN 40 THEN 'FINISHED'
            WHEN 50 THEN 'RESTART'
            ELSE 'NULL'
            END;

UPDATE rpl_task
SET type =
        CASE type
            WHEN 2 THEN 'REPLICA_FULL'
            WHEN 3 THEN 'REPLICA_INC'
            WHEN 101 THEN 'INC_COPY'
            WHEN 100 THEN 'FULL_COPY'
            WHEN 200 THEN 'FULL_VALIDATION'
            WHEN 201 THEN 'RECONCILIATION'
            WHEN 202 THEN 'FULL_VALIDATION_CROSSCHECK'
            WHEN 203 THEN 'RECONCILIATION_CROSSCHECK'
            WHEN 300 THEN 'CDC_INC'
            WHEN 301 THEN 'REC_SEARCH'
            WHEN 302 THEN 'REC_COMBINE'
            ELSE 'NULL'
            END;

UPDATE rpl_service
SET status =
        CASE status
            WHEN 10 THEN 'READY'
            WHEN 20 THEN 'RUNNING'
            WHEN 30 THEN 'STOPPED'
            WHEN 40 THEN 'FINISHED'
            WHEN 50 THEN 'DEPRECATED'
            ELSE 'NULL'
            END;

UPDATE rpl_service
SET service_type =
        CASE service_type
            WHEN 2 THEN 'REPLICA_FULL'
            WHEN 3 THEN 'REPLICA_INC'
            WHEN 101 THEN 'INC_COPY'
            WHEN 100 THEN 'FULL_COPY'
            WHEN 200 THEN 'FULL_VALIDATION'
            WHEN 201 THEN 'RECONCILIATION'
            WHEN 202 THEN 'FULL_VALIDATION_CROSSCHECK'
            WHEN 203 THEN 'RECONCILIATION_CROSSCHECK'
            WHEN 300 THEN 'CDC_INC'
            WHEN 301 THEN 'REC_SEARCH'
            WHEN 302 THEN 'REC_COMBINE'
            ELSE 'NULL'
            END;

UPDATE rpl_state_machine
SET status =
        CASE status
            WHEN 20 THEN 'RUNNING'
            WHEN 30 THEN 'STOPPED'
            WHEN 40 THEN 'FINISHED'
            WHEN 50 THEN 'DEPRECATED'
            ELSE 'NULL'
            END;

UPDATE rpl_state_machine
SET type =
        CASE type
            WHEN 10 THEN 'DATA_IMPORT'
            WHEN 30 THEN 'REPLICA'
            WHEN 40 THEN 'RECOVERY'
            ELSE 'NULL'
            END;

UPDATE rpl_state_machine
SET state =
        CASE state
            WHEN 0 THEN 'INITIAL'
            WHEN 1 THEN 'FINISHED'
            WHEN 101 THEN 'FULL_COPY'
            WHEN 102 THEN 'INC_COPY'
            WHEN 104 THEN 'CATCH_UP_VALIDATION'
            WHEN 105 THEN 'RECONCILIATION'
            WHEN 108 THEN 'RECON_FINISHED_WAIT_CATCH_UP'
            WHEN 106 THEN 'RECON_FINISHED_CATCH_UP'
            WHEN 107 THEN 'BACK_FLOW'
            WHEN 109 THEN 'BACK_FLOW_CATCH_UP'
            WHEN 202 THEN 'REPLICA_INIT'
            WHEN 203 THEN 'REPLICA_FULL'
            WHEN 201 THEN 'REPLICA_INC'
            WHEN 301 THEN 'REC_SEARCH'
            WHEN 302 THEN 'REC_COMBINE'
            ELSE 'NULL'
            END;

UPDATE validation_task
SET state =
        CASE state
            WHEN 101 THEN 'INIT'
            WHEN 102 THEN 'PREPARED'
            WHEN 103 THEN 'COMPARING'
            WHEN 104 THEN 'DONE'
            WHEN 500 THEN 'ERROR'
            ELSE 'NULL'
            END;

UPDATE validation_task
SET type =
        CASE type
            WHEN 1 THEN 'FORWARD'
            WHEN 2 THEN 'BACKWARD'
            ELSE 'NULL'
            END;

UPDATE validation_diff
SET state =
        CASE state
            WHEN 201 THEN 'INIT'
            WHEN 202 THEN 'FIXED'
            WHEN 203 THEN 'RECHECKED'
            ELSE 'NULL'
            END;

UPDATE validation_diff
SET type =
        CASE type
            WHEN 1 THEN 'FORWARD'
            WHEN 2 THEN 'BACKWARD'
            ELSE 'NULL'
            END;

UPDATE rpl_ddl_main
SET state =
        CASE state
            WHEN 0 THEN 'NOT_START'
            WHEN 10 THEN 'RUNNING'
            WHEN 20 THEN 'SUCCEED'
            WHEN 30 THEN 'FAILED'
            ELSE 'NULL'
            END;

UPDATE rpl_ddl_sub
SET state =
        CASE state
            WHEN 0 THEN 'NOT_START'
            WHEN 10 THEN 'RUNNING'
            WHEN 20 THEN 'SUCCEED'
            WHEN 30 THEN 'FAILED'
            ELSE 'NULL'
            END;

DELIMITER $$
drop procedure IF EXISTS `redefineColumn` $$
create procedure redefineColumn(IN tableName varchar(60), in columnName varchar(20), in ddlAction varchar(10),
                                in typeAndCommentPrefix varchar(300))
BEGIN
    DECLARE V_SQL VARCHAR(500);
    set @V_SQL = '';
    IF ddlAction = 'ADD' THEN
        IF NOT EXISTS(select *
                      from information_schema.columns
                      where table_schema = (select database())
                        and table_name = tableName
                        and column_name = columnName)
        THEN
            set V_SQL = CONCAT('alter table `', tableName, '` add column `', columnName, '` ', typeAndCommentPrefix);
            set @V_SQL = V_SQL;
        END IF;
    END IF;

    IF ddlAction = 'DROP'
    THEN
        IF EXISTS(select *
                  from information_schema.columns
                  where table_schema = (select database())
                    and table_name = tableName
                    and column_name = columnName)
        THEN
            set V_SQL = CONCAT('alter table `', tableName, '` drop column `', columnName, '`');
            set @V_SQL = V_SQL;
        END IF;
    END IF;

    IF ddlAction = 'MODIFY'
    THEN
        IF EXISTS(select *
                  from information_schema.columns
                  where table_schema = (select database())
                    and table_name = tableName
                    and column_name = columnName)
        THEN
            set V_SQL = CONCAT('alter table `', tableName, '` modify column `', columnName, '` ', typeAndCommentPrefix);
            set @V_SQL = V_SQL;
        END IF;
    END IF;
    IF @v_sql != '' THEN
        PREPARE stmt FROM @v_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;


call redefineColumn('rpl_stat_metrics', 'total_commit_count', 'ADD',
                    'bigint(20) unsigned not null default 0 COMMENT ''统计写入到目标的数据总量'' ');
