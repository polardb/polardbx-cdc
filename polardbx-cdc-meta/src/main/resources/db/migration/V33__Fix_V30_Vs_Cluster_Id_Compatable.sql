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

create TABLE if not exists `binlog_polarx_command`
(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created`  timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
    `cmd_id`       varchar(40)         NOT NULL COMMENT '命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0',
    `cmd_type`     varchar(32)         NOT NULL DEFAULT '' COMMENT '命令类型',
    `cmd_request`  TEXT                         DEFAULT NULL COMMENT '命令请求内容',
    `cmd_reply`    TEXT                         DEFAULT NULL COMMENT '命令响应内容',
    `cmd_status`   BIGINT(10)          NOT NULL DEFAULT 0 COMMENT '命令处理状态,0-待处理，1-处理成功，2-处理失败',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cmd_id_type` (`cmd_id`, `cmd_type`),
    KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

call redefineColumn('binlog_polarx_command', 'cmd_id', 'MODIFY',
                    'varchar(120) NOT NULL COMMENT ''命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0'' ');
call redefineColumn('binlog_oss_record', 'cluster_id', 'ADD', 'varchar(64) default ''0'' ');
# 上面是为了处理v30不会被重复执行问题，可能漏掉的内容，在这里补上

# 补上cluster_id ， 解决老版本cn的兼容性问题
call redefineColumn('binlog_polarx_command', 'cluster_id', 'ADD',
                    'varchar(64) default ''0'' ');
