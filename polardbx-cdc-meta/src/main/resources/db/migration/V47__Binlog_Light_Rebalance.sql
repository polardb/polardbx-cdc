DELIMITER $$
DROP PROCEDURE IF EXISTS `add_sub_version_for_some_tables_v47` $$
CREATE PROCEDURE add_sub_version_for_some_tables_v47()
BEGIN
    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_task_config'
                    AND column_name = 'sub_version')
    THEN
        alter table binlog_task_config
            add column sub_version bigint(20) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_task_info'
                    AND column_name = 'sub_version')
    THEN
        alter table binlog_task_info
            add column sub_version bigint(20) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_dumper_info'
                    AND column_name = 'sub_version')
    THEN
        alter table binlog_dumper_info
            add column sub_version bigint(20) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_schedule_history'
                    AND column_name = 'sub_version')
    THEN
        alter table binlog_schedule_history
            add column sub_version bigint(20) NOT NULL DEFAULT 1;
    END IF;
END $$
DELIMITER ;
CALL add_sub_version_for_some_tables_v47;


DELIMITER $$
drop procedure IF EXISTS `modify_unique_key_for_binlog_schedule_history` $$
create procedure modify_unique_key_for_binlog_schedule_history()
BEGIN
    IF EXISTS(SELECT *
              FROM information_schema.statistics
              WHERE table_schema = (select database())
                AND table_name = 'binlog_schedule_history'
                AND INDEX_NAME = 'uindex_key')
    THEN
        alter table `binlog_schedule_history`
            drop index `uindex_key`;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.statistics
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_schedule_history'
                    AND INDEX_NAME = 'uk_cluster_version')
    THEN
        alter table `binlog_schedule_history`
            add unique index `uk_cluster_version` (`cluster_id`, `version`, `sub_version`);
    END IF;
END $$
DELIMITER ;
call modify_unique_key_for_binlog_schedule_history;


DELIMITER $$
DROP PROCEDURE IF EXISTS `add_enable_light_rebalance_column_for_some_tables_v47` $$
CREATE PROCEDURE add_enable_light_rebalance_column_for_some_tables_v47()
BEGIN
    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_node_info'
                    AND column_name = 'enable_light_rebalance')
    THEN
        alter table binlog_node_info
            add column enable_light_rebalance boolean NOT NULL DEFAULT false;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_dumper_info'
                    AND column_name = 'enable_light_rebalance')
    THEN
        alter table binlog_dumper_info
            add column enable_light_rebalance boolean NOT NULL DEFAULT false;
    END IF;

    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_task_info'
                    AND column_name = 'enable_light_rebalance')
    THEN
        alter table binlog_task_info
            add column enable_light_rebalance boolean NOT NULL DEFAULT false;
    END IF;
END $$
DELIMITER ;
CALL add_enable_light_rebalance_column_for_some_tables_v47;