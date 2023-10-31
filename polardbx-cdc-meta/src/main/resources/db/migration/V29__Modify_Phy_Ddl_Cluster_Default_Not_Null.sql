alter table binlog_phy_ddl_history modify column `cluster_id` varchar(64) NOT NULL COMMENT '所属集群';
