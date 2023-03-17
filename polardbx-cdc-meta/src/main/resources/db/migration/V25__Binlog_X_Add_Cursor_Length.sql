
alter table binlog_x_stream modify column `latest_cursor` varchar(600) NULL COMMENT 'binlog文件最新位点';
alter table binlog_node_info modify column `latest_cursor` varchar(600)  not null default '';
