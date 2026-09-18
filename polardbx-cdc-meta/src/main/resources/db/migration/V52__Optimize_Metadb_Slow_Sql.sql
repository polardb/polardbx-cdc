DELIMITER $$
DROP PROCEDURE IF EXISTS `optimize_metadb_slow_sql_v52` $$
CREATE PROCEDURE optimize_metadb_slow_sql_v52()
BEGIN
    -- 1. binlog_phy_ddl_history: 添加 tso 索引（加速 DELETE ... WHERE tso < ? LIMIT N）
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_phy_ddl_history' AND index_name='idx_tso')
    THEN
        ALTER TABLE `binlog_phy_ddl_history` ADD KEY `idx_tso` (`tso`);
    END IF;

    -- 2. binlog_phy_ddl_history: 添加 storage_inst_id 索引（加速 WHERE storage_inst_id = ? 查询）
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_phy_ddl_history' AND index_name='idx_storage_inst_id')
    THEN
        ALTER TABLE `binlog_phy_ddl_history` ADD KEY `idx_storage_inst_id` (`storage_inst_id`);
    END IF;

    -- 3. binlog_oss_record: 添加 STORED 生成列 binlog_file_seq（提取 binlog_file 数字后缀）
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='binlog_file_seq')
    THEN
        ALTER TABLE `binlog_oss_record` ADD COLUMN `binlog_file_seq` INT UNSIGNED GENERATED ALWAYS AS (CAST(SUBSTRING_INDEX(`binlog_file`, '.', -1) AS UNSIGNED)) STORED;
    END IF;

    -- 4. binlog_oss_record: 添加复合索引（覆盖 group_id + stream_id + cluster_id + binlog_file_seq 查询）
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND index_name='idx_group_stream_cluster_seq')
    THEN
        ALTER TABLE `binlog_oss_record` ADD KEY `idx_group_stream_cluster_seq` (`group_id`, `stream_id`, `cluster_id`, `binlog_file_seq`);
    END IF;
END $$
DELIMITER ;
CALL optimize_metadb_slow_sql_v52;
