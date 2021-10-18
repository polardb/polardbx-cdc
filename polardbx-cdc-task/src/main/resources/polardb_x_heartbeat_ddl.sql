create database IF NOT EXISTS __drds_heartbeat__;
CREATE TABLE IF NOT EXISTS __drds_heartbeat__.__drds_heartbeat__ (
	`id` bigint(20) NOT NULL AUTO_INCREMENT BY GROUP,
	`sname` varchar(10) DEFAULT NULL,
	`gmt_modified` datetime(3) DEFAULT NULL,
	PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4  dbpartition by hash(`id`);

CREATE TABLE IF NOT EXISTS __drds_heartbeat__.__drds_heartbeat_single__ (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`sname` varchar(10) DEFAULT NULL,
	`gmt_modified` datetime(3) DEFAULT NULL,
	PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;