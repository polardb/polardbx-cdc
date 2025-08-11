/*
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_dumper_info_v42` $$
CREATE PROCEDURE add_column_binlog_dumper_info_v42()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_dumper_info' AND column_name='delay')
        THEN
        alter table binlog_dumper_info add column delay bigint default 9223372036854775807;
    END IF;
END $$
DELIMITER ;
CALL add_column_binlog_dumper_info_v42;
