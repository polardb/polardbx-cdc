DELIMITER $$
DROP PROCEDURE IF EXISTS `add_column_binlog_oss_record_v9` $$
CREATE PROCEDURE add_column_binlog_oss_record_v9()
BEGIN
    IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='log_begin')
	THEN
	    alter table binlog_oss_record add column log_begin datetime(3) default NULL;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='log_end')
	THEN
	    alter table binlog_oss_record add column log_end datetime(3) default NULL;
	END IF;
	IF NOT EXISTS(SELECT * FROM information_schema.columns WHERE table_schema=(select database()) AND table_name='binlog_oss_record' AND column_name='log_size')
	THEN
        alter table binlog_oss_record add column log_size bigint default 0;
	END IF;
END $$
DELIMITER ;
CALL add_column_binlog_oss_record_v9;
