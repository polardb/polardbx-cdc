alter table binlog_logic_meta_history modify column instruction_id varchar(120) DEFAULT NULL COMMENT 'polarx_command 中的指令id';
