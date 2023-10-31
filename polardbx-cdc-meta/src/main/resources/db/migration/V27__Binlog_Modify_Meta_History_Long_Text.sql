alter table `binlog_phy_ddl_history` modify column `ddl` longtext NOT NULL;
alter table `binlog_logic_meta_history` modify column `topology` longtext;
alter table `binlog_logic_meta_history` modify column `ddl` longtext NOT NULL;
