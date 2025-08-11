/*
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

DELIMITER $$
DROP PROCEDURE IF EXISTS `add_index_binlog_oss_record_v44` $$
CREATE PROCEDURE add_index_binlog_oss_record_v44()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='idx_upload_status')
	THEN
ALTER TABLE `binlog_oss_record` ADD index `idx_upload_status`(`upload_status`);
END IF;

    IF NOT EXISTS(SELECT * FROM information_schema.statistics WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND INDEX_NAME='idx_gmt_modified')
	THEN
ALTER TABLE `binlog_oss_record` ADD index `idx_gmt_modified`(`gmt_modified`);
END IF;
END $$
DELIMITER ;
CALL add_index_binlog_oss_record_v44;