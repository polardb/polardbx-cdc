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

call redefineColumn('binlog_node_info', 'polarx_inst_id', 'ADD', 'varchar(128) DEFAULT NULL');
call redefineColumn('binlog_node_info', 'cluster_role', 'ADD', 'varchar(24) NOT NULL DEFAULT ''master'' COMMENT ''集群角色'' ');
call redefineColumn('binlog_node_info', 'last_tso_heartbeat', 'ADD', 'timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''最近一次上报TSO时间'' ');
call redefineColumn('binlog_task_info', 'polarx_inst_id', 'ADD', 'varchar(128) DEFAULT NULL');
call redefineColumn('binlog_task_info', 'sources_list', 'ADD', 'longtext DEFAULT NULL COMMENT ''task连接的DN列表'' ');
call redefineColumn('binlog_dumper_info', 'polarx_inst_id', 'ADD', 'varchar(128) DEFAULT NULL');

create TABLE if not exists `binlog_polarx_command` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP,
    `cmd_id` varchar(40) NOT NULL COMMENT '命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0',
    `cmd_type` varchar(32) NOT NULL DEFAULT '' COMMENT '命令类型',
    `cmd_request` TEXT DEFAULT NULL COMMENT '命令请求内容',
    `cmd_reply` TEXT DEFAULT NULL COMMENT '命令响应内容',
    `cmd_status` BIGINT(10) NOT NULL DEFAULT 0 COMMENT '命令处理状态,0-待处理，1-处理成功，2-处理失败',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cmd_id_type` (`cmd_id`,`cmd_type`),
    KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

call redefineColumn('binlog_polarx_command', 'cmd_id', 'MODIFY', 'varchar(120) NOT NULL COMMENT ''命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0'' ');
call redefineColumn('binlog_x_stream_group', 'group_name', 'MODIFY', 'varchar(50) NOT NULL COMMENT ''多流分组名称'' ');
call redefineColumn('binlog_x_stream', 'group_name', 'MODIFY', 'varchar(50) NOT NULL COMMENT ''所属分组'' ');
call redefineColumn('binlog_oss_record', 'cluster_id', 'ADD', 'varchar(64) default ''0'' ');


DELIMITER $$
drop procedure IF EXISTS `add_idx_db_name_4_binlog_oss_record` $$
create procedure add_idx_db_name_4_binlog_oss_record()
BEGIN
    IF EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='binlog_oss_record_binlog_file_uindex')
	THEN
	    alter table `binlog_oss_record` drop index `binlog_oss_record_binlog_file_uindex`;
	END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='uk_file_name_cluster_id')
	THEN
	    alter table `binlog_oss_record` add unique index `uk_file_name_cluster_id`(`binlog_file`,`cluster_id`);
	END IF;
END $$
DELIMITER ;

call add_idx_db_name_4_binlog_oss_record;


create TABLE IF NOT EXISTS binlog_lab_event(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    gmt_created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type INT NOT NULL COMMENT 'event类型',
    `desc` VARCHAR(128) COMMENT '描述',
    params TEXT COMMENT 'action参数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
