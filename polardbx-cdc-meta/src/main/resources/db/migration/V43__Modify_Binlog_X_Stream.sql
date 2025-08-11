/*
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_x_stream_v43` $$
CREATE PROCEDURE add_column_binlog_x_stream_v43()
BEGIN
    IF NOT EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_schema = (select database())
                    AND table_name = 'binlog_x_stream'
                    AND column_name = 'status')
    THEN
        alter table binlog_x_stream
            add column status int default 0;
    END IF;
END $$
DELIMITER ;
CALL add_column_binlog_x_stream_v43;
