create TABLE IF NOT EXISTS binlog_x_stream_group(
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `group_name` varchar(200) NOT NULL COMMENT '多流分组名称',
  `group_desc` varchar(200) NOT NULL COMMENT '多流分组描述',
  PRIMARY KEY (`id`),
  unique key uk_group_name(`group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

create TABLE IF NOT EXISTS binlog_x_stream(
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `stream_name` varchar(200) NOT NULL COMMENT 'Stream名称',
  `stream_desc` varchar(200) NOT NULL COMMENT 'Stream描述',
  `group_name` varchar(200) NOT NULL COMMENT '所属分组',
  `expected_storage_tso` varchar(200) NOT NULL default '' COMMENT '期望的storage tso',
  `latest_cursor` varchar(200) NULL COMMENT 'binlog文件最新位点',
  `endpoint` TEXT NULL COMMENT 'endpoint信息',
  PRIMARY KEY (`id`),
  unique key uk_stream_name (`stream_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

create TABLE IF NOT EXISTS binlog_x_table_stream_mapping(
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `db_name` varchar(200) NOT NULL COMMENT 'database name',
  `table_name` varchar(200) NOT NULL COMMENT 'table name',
  `stream_seq` bigint(20) unsigned NOT NULL COMMENT 'stream seq',
  `cluster_id` varchar (64) NOT NULL,
  PRIMARY KEY (`id`),
  unique key uk_db_table (`db_name`,`table_name`,`cluster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

create TABLE IF NOT EXISTS `binlog_storage_history_detail` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
  `cluster_id` varchar (64) NOT NULL,
  `tso` varchar(128) NOT NULL,
  `instruction_id` varchar (50) NOT NULL,
  `stream_name` varchar(200) NOT NUll,
  `status` int(10) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_cts` (`cluster_id`,`tso`,`stream_name`),
  UNIQUE KEY `uindex_cis` (`cluster_id`,`instruction_id`,`stream_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DELIMITER $$
drop procedure IF EXISTS `redefineColumn` $$
create procedure redefineColumn(IN tableName varchar(60), in columnName varchar(20), in ddlAction varchar(10), in typeAndCommentPrefix varchar(300))
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

call redefineColumn('binlog_task_info', 'role', 'MODIFY', 'varchar(20) DEFAULT NULL COMMENT ''Final | Relay | Dispatcher'' ');
call redefineColumn('binlog_schedule_history', 'cluster_id', 'ADD', 'varchar(64) NOT NULL default ''0'' COMMENT ''所属集群，0代表全局binlog集群'' ');
call modify_uinque_idx('binlog_schedule_history','uindex_key','(`version`,`cluster_id`)');

# group_id的默认值需要与Constants.java中的GROUP_NAME_GLOBAL保持一致
call redefineColumn('binlog_oss_record', 'group_id', 'ADD', 'varchar(100) default ''group_global'' comment ''group name， 单流为group_global'' ');
# stream_id的默认值需要与Constants.java中的STREAM_NAME_GLOBAL保持一致
call redefineColumn('binlog_oss_record', 'stream_id', 'ADD','varchar(100) default ''stream_global'' comment ''stream name，单流为stream_global'' ');
call redefineColumn('binlog_oss_record', 'last_tso', 'ADD','varchar(128) DEFAULT NULL comment ''binlog 文件 最大tso'' ');
call redefineColumn('binlog_oss_record', 'binlog_file', 'MODIFY', 'varchar(128) NOT NULL comment ''binlog文件名'' ');

call redefineColumn('binlog_logic_meta_history', 'instruction_id', 'ADD','varchar(50) DEFAULT NULL comment ''polarx_command 中的指令id'' ');
call redefineColumn('binlog_polarx_command', 'cmd_id', 'MODIFY','varchar(50) DEFAULT NULL comment ''命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0'' ');

call redefineColumn('binlog_storage_history', 'cluster_id', 'ADD','varchar(64) NOT NULL default ''0'' COMMENT ''所属集群，0代表全局binlog集群'' ');
call redefineColumn('binlog_storage_history', 'group_name', 'ADD','varchar(200) NOT NULL default ''group_global'' COMMENT ''所属流组'' ');
call modify_uinque_idx('binlog_storage_history','uindex_tso','(`tso`,`cluster_id`)');
call modify_uinque_idx('binlog_storage_history','uindex_instructionId','(`instruction_id`,`cluster_id`)');

call redefineColumn('binlog_phy_ddl_history', 'cluster_id', 'ADD','varchar(64) NOT NULL default ''0'' COMMENT ''所属集群，0代表全局binlog集群'' ');
call modify_uinque_idx('binlog_phy_ddl_history','udx_tso_db','(`tso`,`db_name`,`cluster_id`)');

call redefineColumn('binlog_logic_meta_history','delete','ADD','tinyint(1) default 0 comment ''是否删除，实验室环境使用''');