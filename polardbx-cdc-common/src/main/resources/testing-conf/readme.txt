# gms_tables.sql的构建方式
1. 跑通一个binlog 单流&多流实验室：https://test.aone.alibaba-inc.com/project/815516/jobs/1972550?buildId=196149261
2. 登录DN-0节点，然后执行该命令:
   mysqldump -h127.1 --no-data --databases polardbx_meta_db --skip-lock-tables --compatible=no_key_options,no_table_options > tables.sql

# gms_cdc_tables_and_data.sql的构建方式
1. 跑通一个binlog 单流&多流实验室：https://test.aone.alibaba-inc.com/project/815516/jobs/1972550?buildId=196149261
2. 登录DN-0节点，然后执行该命令:
   mysqldump -h127.1  --databases polardbx_meta_db  --skip-lock-tables  --compatible=no_key_options,no_table_options  --tables binlog_dumper_info binlog_env_config_history binlog_lab_event binlog_logic_meta_history binlog_node_info binlog_oss_record binlog_phy_ddl_hist_clean_point binlog_phy_ddl_history binlog_polarx_command binlog_schedule_history binlog_schema_history binlog_semi_snapshot binlog_storage_history binlog_storage_history_detail binlog_storage_sequence binlog_system_config binlog_task_config binlog_task_info binlog_x_stream binlog_x_stream_group binlog_x_table_stream_mapping    > tables_cdc.sql


