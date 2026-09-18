DELIMITER $$
DROP PROCEDURE IF EXISTS `add_index_binlog_oss_record_v51` $$
CREATE PROCEDURE add_index_binlog_oss_record_v51()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='idx_stream_purge')
	THEN
ALTER TABLE `binlog_oss_record` ADD index `idx_stream_purge`(`stream_id`, `purge_status`);
END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='idx_group_stream_cluster_purge')
	THEN
ALTER TABLE `binlog_oss_record` ADD index `idx_group_stream_cluster_purge`(`group_id`, `stream_id`, `cluster_id`, `purge_status`);
END IF;
END $$
DELIMITER ;
CALL add_index_binlog_oss_record_v51;
