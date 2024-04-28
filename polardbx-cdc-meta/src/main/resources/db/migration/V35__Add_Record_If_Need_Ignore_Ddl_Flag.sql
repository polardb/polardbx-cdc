DELIMITER $$
drop procedure IF EXISTS `redefineColumn34` $$
create procedure redefineColumn34(IN tableName varchar(60), in columnName varchar(20), in ddlAction varchar(10),
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

call redefineColumn34('binlog_logic_meta_history', 'need_apply', 'ADD', 'tinyint(1) default 1 not null');
