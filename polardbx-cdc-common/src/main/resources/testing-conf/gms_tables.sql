-- MySQL dump 10.13  Distrib 5.7.14.5-AliSQL-X-Cluster-1.6.1.5, for Linux (x86_64)
--
-- Host: 127.1    Database: polardbx_meta_db
-- ------------------------------------------------------
-- Server version	5.7.38-AliSQL-X-Cluster-1.6.1.5-20230807-log
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO,NO_KEY_OPTIONS,NO_TABLE_OPTIONS' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `polardbx_meta_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `polardbx_meta_db` /*!40100 DEFAULT CHARACTER SET utf8 */;

USE `polardbx_meta_db`;

--
-- Table structure for table `__test_sequence`
--

DROP TABLE IF EXISTS `__test_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `__test_sequence` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `new_name` varchar(128) DEFAULT NULL,
  `value` bigint(20) NOT NULL,
  `unit_count` int(11) NOT NULL DEFAULT '1',
  `unit_index` int(11) NOT NULL DEFAULT '0',
  `inner_step` int(11) NOT NULL DEFAULT '100000',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `__test_sequence_opt`
--

DROP TABLE IF EXISTS `__test_sequence_opt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `__test_sequence_opt` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `new_name` varchar(128) DEFAULT NULL,
  `value` bigint(20) unsigned NOT NULL,
  `increment_by` int(10) unsigned NOT NULL DEFAULT '1',
  `start_with` bigint(20) unsigned NOT NULL DEFAULT '1',
  `max_value` bigint(20) unsigned NOT NULL DEFAULT '18446744073709551615',
  `cycle` tinyint(5) unsigned NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `audit_log` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `user_name` varchar(128) NOT NULL,
  `host` varchar(128) NOT NULL,
  `port` int(128) NOT NULL,
  `schema` varchar(128) NOT NULL,
  `audit_info` varchar(1024) DEFAULT NULL,
  `action` varchar(16) DEFAULT NULL COMMENT 'LOGIN, LOGOUT, LOGIN_ERR, CREATE_USER, DROP_USER, GRANT, REVOKE, SET_PASSWORD',
  `trace_id` varchar(64) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_name_key` (`user_name`),
  KEY `action_key` (`action`),
  KEY `gmt_created_key` (`gmt_created`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `backfill_objects`
--

DROP TABLE IF EXISTS `backfill_objects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `backfill_objects` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `index_schema` varchar(64) NOT NULL,
  `index_name` varchar(64) NOT NULL,
  `physical_db` varchar(128) DEFAULT NULL COMMENT 'Group key',
  `physical_table` varchar(128) DEFAULT NULL COMMENT 'Physical table name',
  `column_index` bigint(20) NOT NULL DEFAULT '-1' COMMENT 'Column index in index table',
  `parameter_method` varchar(64) DEFAULT NULL COMMENT 'Parameter method for applying LAST_VALUE to extractor',
  `last_value` longtext,
  `max_value` longtext,
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:INIT,1:RUNNING,2:SUCCESS,3:FAILED',
  `message` longtext,
  `success_row_count` bigint(20) unsigned NOT NULL,
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `extra` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_job_db_tb_column` (`job_id`,`physical_db`,`physical_table`,`column_index`),
  KEY `i_job_id` (`job_id`),
  KEY `i_job_id_status` (`job_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `baseline_info`
--

DROP TABLE IF EXISTS `baseline_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `baseline_info` (
  `id` bigint(20) NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sql` mediumtext NOT NULL,
  `table_set` text NOT NULL,
  `tables_hashcode` bigint(20) NOT NULL,
  `extend_field` longtext COMMENT 'Json string extend field',
  PRIMARY KEY (`schema_name`,`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_dumper_info`
--

DROP TABLE IF EXISTS `binlog_dumper_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_dumper_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `task_name` varchar(64) NOT NULL COMMENT 'Dumper',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `port` int(10) NOT NULL,
  `role` varchar(8) DEFAULT NULL COMMENT 'M:Master, S:Slaver',
  `status` int(10) NOT NULL DEFAULT '0' COMMENT '0:提供服务，其他状态非0',
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `container_id` varchar(50) NOT NULL DEFAULT '',
  `version` bigint(20) NOT NULL DEFAULT '0',
  `polarx_inst_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_cluster_ip_port` (`cluster_id`,`ip`,`port`),
  UNIQUE KEY `udx_taskname` (`cluster_id`,`task_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_env_config_history`
--

DROP TABLE IF EXISTS `binlog_env_config_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_env_config_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `change_env_content` mediumtext NOT NULL,
  `instruction_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_tso` (`tso`),
  UNIQUE KEY `uindex_instructionId` (`instruction_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_lab_event`
--

DROP TABLE IF EXISTS `binlog_lab_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_lab_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `event_type` int(11) NOT NULL COMMENT 'event类型',
  `desc` varchar(128) DEFAULT NULL COMMENT '描述',
  `params` text COMMENT 'action参数',
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_logic_meta_history`
--

DROP TABLE IF EXISTS `binlog_logic_meta_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_logic_meta_history` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `db_name` varchar(128) NOT NULL DEFAULT '' COMMENT '引擎db_info表是128',
  `table_name` varchar(128) DEFAULT NULL COMMENT '当前操作的表名',
  `sql_kind` varchar(128) DEFAULT NULL COMMENT 'DDL类型',
  `ddl` longtext NOT NULL,
  `topology` longtext,
  `type` tinyint(11) NOT NULL COMMENT '1:快照，2：DDL',
  `ext_info` mediumtext COMMENT '扩展信息',
  `ddl_record_id` bigint(20) DEFAULT NULL COMMENT '打标记录对应的id',
  `ddl_job_id` bigint(20) DEFAULT NULL COMMENT '打标记录对应的job_id',
  `instruction_id` varchar(120) DEFAULT NULL COMMENT 'polarx_command 中的指令id',
  `delete` tinyint(1) DEFAULT '0' COMMENT '是否删除，实验室环境使用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_tso_db` (`tso`,`db_name`),
  KEY `idx_ddl_record_id` (`ddl_record_id`),
  KEY `idx_logic_db_name` (`db_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_node_info`
--

DROP TABLE IF EXISTS `binlog_node_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_node_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `container_id` varchar(64) NOT NULL COMMENT '容器id',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `daemon_port` int(10) NOT NULL COMMENT 'daemon 守护端口，一台物理机可能部署多个容器，ip一样，需要port进一步区分',
  `available_ports` varchar(128) NOT NULL COMMENT '可用端口逗号(,)分隔',
  `status` int(10) DEFAULT NULL,
  `core` bigint(10) DEFAULT NULL COMMENT 'CPU数',
  `mem` bigint(10) DEFAULT NULL COMMENT '内存大小(MB)',
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `latest_cursor` varchar(600) NOT NULL DEFAULT '',
  `role` varchar(8) DEFAULT '' COMMENT 'M:Master, S:Slaver',
  `cluster_type` varchar(32) DEFAULT NULL,
  `group_name` varchar(200) DEFAULT NULL COMMENT '所属分组',
  `polarx_inst_id` varchar(128) DEFAULT NULL,
  `cluster_role` varchar(24) NOT NULL DEFAULT 'master' COMMENT '集群角色',
  `last_tso_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次上报TSO时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_container_id` (`container_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_oss_record`
--

DROP TABLE IF EXISTS `binlog_oss_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_oss_record` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `binlog_file` varchar(128) NOT NULL COMMENT 'binlog文件名',
  `upload_status` int(11) DEFAULT '0',
  `purge_status` int(11) DEFAULT '0',
  `upload_host` varchar(32) DEFAULT NULL,
  `log_begin` datetime(3) DEFAULT NULL,
  `log_end` datetime(3) DEFAULT NULL,
  `log_size` bigint(20) DEFAULT '0',
  `last_tso` varchar(128) DEFAULT NULL COMMENT 'binlog文件最大tso',
  `group_id` varchar(100) DEFAULT 'group_global' COMMENT 'group name， 单流为group_global',
  `stream_id` varchar(100) DEFAULT 'stream_global' COMMENT 'stream name，单流为stream_global',
  `cluster_id` varchar(64) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_name_cluster_id` (`binlog_file`,`cluster_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_phy_ddl_hist_clean_point`
--

DROP TABLE IF EXISTS `binlog_phy_ddl_hist_clean_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_phy_ddl_hist_clean_point` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `storage_inst_id` varchar(128) DEFAULT NULL,
  `ext` mediumtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_storage_inst_id` (`storage_inst_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_phy_ddl_history`
--

DROP TABLE IF EXISTS `binlog_phy_ddl_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_phy_ddl_history` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL DEFAULT '',
  `binlog_file` varchar(32) DEFAULT NULL,
  `pos` int(11) DEFAULT NULL,
  `storage_inst_id` varchar(128) DEFAULT NULL,
  `db_name` varchar(128) NOT NULL DEFAULT '' COMMENT '引擎db_info表是128',
  `ddl` longtext NOT NULL,
  `extra` varchar(256) DEFAULT NULL,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_tso_db` (`cluster_id`,`storage_inst_id`,`tso`),
  KEY `idx_phy_db_name` (`db_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_polarx_command`
--

DROP TABLE IF EXISTS `binlog_polarx_command`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_polarx_command` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cmd_id` varchar(120) NOT NULL COMMENT '命令请求id，如果某种类型的命令只需要发送一次，cmd_id设置为常数0',
  `cmd_type` varchar(32) NOT NULL DEFAULT '' COMMENT '命令类型',
  `cmd_request` text COMMENT '命令请求内容',
  `cmd_reply` text COMMENT '命令响应内容',
  `cmd_status` bigint(10) NOT NULL DEFAULT '0' COMMENT '命令处理状态,0-待处理，1-处理成功，2-处理失败',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmd_id_type` (`cmd_id`,`cmd_type`),
  KEY `idx_gmt_modified` (`gmt_modified`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_schedule_history`
--

DROP TABLE IF EXISTS `binlog_schedule_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_schedule_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` bigint(20) NOT NULL,
  `content` mediumtext NOT NULL,
  `cluster_id` varchar(64) NOT NULL DEFAULT '0' COMMENT '所属集群，0代表全局binlog集群',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_key` (`version`,`cluster_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_schema_history`
--

DROP TABLE IF EXISTS `binlog_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_schema_history` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `binlog_schema_history_s_idx` (`success`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_semi_snapshot`
--

DROP TABLE IF EXISTS `binlog_semi_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_semi_snapshot` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `storage_inst_id` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_storage_tso` (`storage_inst_id`,`tso`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_storage_history`
--

DROP TABLE IF EXISTS `binlog_storage_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_storage_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tso` varchar(128) NOT NULL,
  `storage_content` mediumtext NOT NULL,
  `status` int(10) NOT NULL,
  `instruction_id` varchar(128) DEFAULT NULL,
  `cluster_id` varchar(64) NOT NULL DEFAULT '0' COMMENT '所属集群，0代表全局binlog集群',
  `group_name` varchar(200) NOT NULL DEFAULT 'group_global' COMMENT '所属流组',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_tso` (`tso`,`cluster_id`),
  UNIQUE KEY `uindex_instructionId` (`instruction_id`,`cluster_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_storage_history_detail`
--

DROP TABLE IF EXISTS `binlog_storage_history_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_storage_history_detail` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL,
  `tso` varchar(128) NOT NULL,
  `instruction_id` varchar(50) NOT NULL,
  `stream_name` varchar(200) NOT NULL,
  `status` int(10) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_cts` (`cluster_id`,`tso`,`stream_name`),
  UNIQUE KEY `uindex_cis` (`cluster_id`,`instruction_id`,`stream_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_storage_sequence`
--

DROP TABLE IF EXISTS `binlog_storage_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_storage_sequence` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `storage_inst_id` varchar(128) NOT NULL,
  `storage_seq` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_storage_inst_id` (`storage_inst_id`),
  UNIQUE KEY `uindex_storage_seq` (`storage_seq`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_system_config`
--

DROP TABLE IF EXISTS `binlog_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_system_config` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `config_key` varchar(128) NOT NULL,
  `config_value` mediumtext NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_key` (`config_key`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_task_config`
--

DROP TABLE IF EXISTS `binlog_task_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_task_config` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `container_id` varchar(64) NOT NULL COMMENT '容器id',
  `task_name` varchar(64) NOT NULL COMMENT 'Dumper | Final_1 | Relay_1',
  `vcpu` int(10) DEFAULT NULL COMMENT '虚拟cpu占比，目前无意义',
  `mem` int(10) DEFAULT NULL COMMENT '分配内存值，单位mb',
  `ip` varchar(64) NOT NULL,
  `port` int(10) NOT NULL COMMENT '预分配的端口',
  `config` longtext NOT NULL COMMENT 'db黑名单 tb黑名单',
  `role` varchar(64) NOT NULL COMMENT 'Dumper | Final | Relay',
  `status` int(10) NOT NULL DEFAULT '0' COMMENT '1:自动调度开启，其他状态非0',
  `version` bigint(20) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_cluster_task_name` (`cluster_id`,`task_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_task_info`
--

DROP TABLE IF EXISTS `binlog_task_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_task_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cluster_id` varchar(64) NOT NULL COMMENT '所属集群',
  `task_name` varchar(64) NOT NULL COMMENT 'Final_1 | Relay_1',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `port` int(10) NOT NULL,
  `role` varchar(20) DEFAULT NULL COMMENT 'Final | Relay | Dispatcher',
  `status` int(10) NOT NULL DEFAULT '0' COMMENT '0:提供服务，其他状态非0',
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `container_id` varchar(50) NOT NULL DEFAULT '',
  `version` bigint(20) NOT NULL DEFAULT '0',
  `polarx_inst_id` varchar(128) DEFAULT NULL,
  `sources_list` longtext COMMENT 'task连接的DN列表',
  PRIMARY KEY (`id`),
  UNIQUE KEY `udx_cluster_ip_port` (`cluster_id`,`ip`,`port`),
  UNIQUE KEY `udx_taskname` (`cluster_id`,`task_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_x_stream`
--

DROP TABLE IF EXISTS `binlog_x_stream`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_x_stream` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `stream_name` varchar(200) NOT NULL COMMENT 'Stream名称',
  `stream_desc` varchar(200) NOT NULL COMMENT 'Stream描述',
  `group_name` varchar(50) NOT NULL COMMENT '所属分组',
  `expected_storage_tso` varchar(200) NOT NULL DEFAULT '' COMMENT '期望的storage tso',
  `latest_cursor` varchar(600) DEFAULT NULL COMMENT 'binlog文件最新位点',
  `endpoint` text COMMENT 'endpoint信息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stream_name` (`stream_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_x_stream_group`
--

DROP TABLE IF EXISTS `binlog_x_stream_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_x_stream_group` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `group_name` varchar(50) NOT NULL COMMENT '多流分组名称',
  `group_desc` varchar(200) NOT NULL COMMENT '多流分组描述',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_name` (`group_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `binlog_x_table_stream_mapping`
--

DROP TABLE IF EXISTS `binlog_x_table_stream_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `binlog_x_table_stream_mapping` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `db_name` varchar(200) NOT NULL COMMENT 'database name',
  `table_name` varchar(200) NOT NULL COMMENT 'table name',
  `stream_seq` bigint(20) unsigned NOT NULL COMMENT 'stream seq',
  `cluster_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_db_table` (`db_name`,`table_name`,`cluster_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `changeset_objects`
--

DROP TABLE IF EXISTS `changeset_objects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `changeset_objects` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `changeset_id` bigint(20) unsigned NOT NULL,
  `job_id` bigint(20) unsigned NOT NULL,
  `root_job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `index_schema` varchar(64) NOT NULL,
  `index_name` varchar(64) NOT NULL,
  `physical_db` varchar(128) DEFAULT NULL COMMENT 'Group key',
  `physical_table` varchar(64) DEFAULT NULL COMMENT 'Physical table name',
  `fetch_times` bigint(20) unsigned NOT NULL,
  `replay_times` bigint(20) unsigned NOT NULL,
  `delete_row_count` bigint(20) unsigned NOT NULL,
  `replace_row_count` bigint(20) unsigned NOT NULL,
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:INIT,1:RUNNING,2:SUCCESS,3:FAILED',
  `message` longtext,
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fetch_start_time` datetime DEFAULT NULL,
  `end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `extra` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_cs_db_tb` (`changeset_id`,`physical_db`,`physical_table`),
  KEY `i_cs_id` (`changeset_id`),
  KEY `i_cs_id_status` (`changeset_id`,`status`),
  KEY `i_job_id_status` (`job_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `character_sets`
--

DROP TABLE IF EXISTS `character_sets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `character_sets` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `character_set_name` varchar(32) NOT NULL,
  `default_collate_name` varchar(32) NOT NULL,
  `description` varchar(60) DEFAULT NULL,
  `maxlen` bigint(3) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `checker_reports`
--

DROP TABLE IF EXISTS `checker_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `checker_reports` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `index_schema` varchar(64) NOT NULL,
  `index_name` varchar(64) DEFAULT NULL,
  `physical_db` varchar(128) DEFAULT NULL COMMENT 'Group key',
  `physical_table` varchar(64) DEFAULT NULL COMMENT 'Physical table name',
  `error_type` varchar(128) DEFAULT NULL COMMENT 'Check error type',
  `timestamp` datetime DEFAULT NULL COMMENT 'Error found time',
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:FOUND,1:REPAIRED,2:START,3:FINISH',
  `primary_key` longtext,
  `details` longtext,
  `extra` longtext,
  PRIMARY KEY (`id`),
  KEY `i_job_id` (`job_id`),
  KEY `i_job_id_status` (`job_id`,`status`),
  KEY `i_index_name_job_id` (`index_name`,`job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collation_character_set_applicability`
--

DROP TABLE IF EXISTS `collation_character_set_applicability`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `collation_character_set_applicability` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `collation_name` varchar(32) NOT NULL,
  `character_set_name` varchar(32) NOT NULL,
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collations`
--

DROP TABLE IF EXISTS `collations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `collations` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `collation_name` varchar(32) NOT NULL,
  `character_set_name` varchar(32) NOT NULL,
  `is_default` varchar(3) DEFAULT NULL,
  `is_compiled` varchar(3) NOT NULL,
  `sortlen` bigint(3) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `column_evolution`
--

DROP TABLE IF EXISTS `column_evolution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `column_evolution` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `field_id` bigint(11) NOT NULL,
  `ts` bigint(20) unsigned NOT NULL DEFAULT '1',
  `column_record` text,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `field_id` (`field_id`,`ts`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `column_mapping`
--

DROP TABLE IF EXISTS `column_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `column_mapping` (
  `field_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(64) NOT NULL,
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '0:ABSENT,1:PUBLIC',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`field_id`),
  UNIQUE KEY `table_schema` (`table_schema`,`table_name`,`column_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `column_metas`
--

DROP TABLE IF EXISTS `column_metas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `column_metas` (
  `column_meta_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `table_file_name` varchar(1000) NOT NULL,
  `table_schema` varchar(255) NOT NULL,
  `table_name` varchar(255) NOT NULL,
  `stripe_index` bigint(20) NOT NULL,
  `stripe_offset` bigint(20) NOT NULL,
  `stripe_length` bigint(20) NOT NULL,
  `column_name` varchar(255) NOT NULL,
  `column_index` bigint(20) NOT NULL,
  `bloom_filter_path` varchar(255) NOT NULL,
  `bloom_filter_offset` bigint(20) DEFAULT NULL,
  `bloom_filter_length` bigint(20) DEFAULT NULL,
  `is_merged` bigint(20) NOT NULL DEFAULT '0' COMMENT '1: column index data merged in one file',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `task_id` bigint(20) unsigned NOT NULL,
  `life_cycle` bigint(21) unsigned NOT NULL COMMENT '0: creating, 1: ready, 2: deleted',
  `engine` varchar(50) DEFAULT 'OSS',
  `logical_schema_name` varchar(255) NOT NULL,
  `logical_table_name` varchar(255) NOT NULL,
  PRIMARY KEY (`column_meta_id`),
  KEY `table_index` (`table_schema`,`table_name`),
  KEY `task_id` (`task_id`),
  KEY `column_meta_index` (`table_file_name`(255),`column_index`,`stripe_index`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `column_statistics`
--

DROP TABLE IF EXISTS `column_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `column_statistics` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `schema_name` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(64) NOT NULL,
  `cardinality` bigint(20) NOT NULL,
  `cmsketch` longtext NOT NULL,
  `histogram` longtext NOT NULL,
  `null_count` bigint(20) NOT NULL,
  `sample_rate` float NOT NULL,
  `TOPN` longtext,
  `extend_field` longtext COMMENT 'Json string extend field',
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`table_name`,`column_name`),
  KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `columns`
--

DROP TABLE IF EXISTS `columns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `columns` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(64) NOT NULL,
  `ordinal_position` bigint(20) unsigned NOT NULL DEFAULT '0',
  `column_default` longtext,
  `is_nullable` varchar(3) NOT NULL,
  `data_type` varchar(64) NOT NULL,
  `character_maximum_length` bigint(20) unsigned DEFAULT NULL,
  `character_octet_length` bigint(20) unsigned DEFAULT NULL,
  `numeric_precision` bigint(20) unsigned DEFAULT NULL,
  `numeric_scale` bigint(20) unsigned DEFAULT NULL,
  `datetime_precision` bigint(20) unsigned DEFAULT NULL,
  `character_set_name` varchar(32) DEFAULT NULL,
  `collation_name` varchar(32) DEFAULT NULL,
  `column_type` longtext NOT NULL,
  `column_key` varchar(3) DEFAULT NULL,
  `extra` varchar(60) DEFAULT NULL,
  `privileges` varchar(80) DEFAULT NULL,
  `column_comment` varchar(1024) DEFAULT NULL,
  `generation_expression` longtext NOT NULL,
  `jdbc_type` int(11) NOT NULL,
  `jdbc_type_name` varchar(64) NOT NULL,
  `field_length` bigint(20) NOT NULL DEFAULT '0',
  `version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `flag` bigint(20) unsigned DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_schema` (`table_schema`,`table_name`,`column_name`),
  KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complex_task_outline`
--

DROP TABLE IF EXISTS `complex_task_outline`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `complex_task_outline` (
  `id` bigint(21) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `tg_name` varchar(64) NOT NULL DEFAULT '-' COMMENT 'tablegroup name',
  `object_name` varchar(128) NOT NULL,
  `type` int(11) NOT NULL COMMENT '1:split, 2:merge, 3:move',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:creating,1:delete_only,2:xx',
  `extra` longtext,
  `source_sql` text,
  `sub_task` int(11) NOT NULL COMMENT '0:parent task, 1:sub task',
  PRIMARY KEY (`id`),
  KEY `k_sch_job_id_obj` (`table_schema`,`job_id`,`object_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `concurrency_control_rule`
--

DROP TABLE IF EXISTS `concurrency_control_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `concurrency_control_rule` (
  `id` char(100) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `sql_type` char(6) NOT NULL,
  `db_name` char(64) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `table_name` char(64) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `user_name` char(32) CHARACTER SET utf8 COLLATE utf8_unicode_ci DEFAULT NULL,
  `client_ip` char(60) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `parallelism` int(11) NOT NULL DEFAULT '0' COMMENT 'value of MAX_CONCURRENCY in the CREATE CCL_RULE statement',
  `keywords` varchar(512) DEFAULT NULL,
  `template_id` text,
  `queue_size` int(11) NOT NULL DEFAULT '0' COMMENT 'value of WAIT_QUEUE_SIZE in the CREATE CCL_RULE statement',
  `priority` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_priority` int(11) NOT NULL,
  `wait_timeout` int(11) NOT NULL DEFAULT '600',
  `fast_match` int(11) NOT NULL DEFAULT '0',
  `light_wait` int(11) NOT NULL DEFAULT '0',
  `query` text,
  `params` text,
  `query_template_id` char(8) DEFAULT NULL,
  `inst_id` varchar(128) NOT NULL,
  `gmt_updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`priority`),
  UNIQUE KEY `inst_id` (`inst_id`,`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `concurrency_control_trigger`
--

DROP TABLE IF EXISTS `concurrency_control_trigger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `concurrency_control_trigger` (
  `id` char(50) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `priority` int(11) NOT NULL AUTO_INCREMENT,
  `conditions` text,
  `rule_config` text,
  `schema` char(64) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `rule_upgrade` int(11) NOT NULL DEFAULT '0',
  `max_ccl_rule` int(11) NOT NULL DEFAULT '0',
  `ccl_rule_count` int(11) NOT NULL DEFAULT '0',
  `max_sql_size` int(11) NOT NULL DEFAULT '4096',
  `gmt_updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `inst_id` char(128) NOT NULL,
  PRIMARY KEY (`priority`),
  UNIQUE KEY `id` (`inst_id`,`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_listener`
--

DROP TABLE IF EXISTS `config_listener`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_listener` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `data_id` varchar(200) NOT NULL,
  `status` int(11) NOT NULL COMMENT '0:normal, 1:removed',
  `op_version` bigint(20) NOT NULL,
  `extras` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_id` (`data_id`),
  KEY `idx_modify_ts` (`gmt_modified`),
  KEY `idx_status` (`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `db_group_info`
--

DROP TABLE IF EXISTS `db_group_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `db_group_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `db_name` varchar(128) NOT NULL,
  `group_name` varchar(128) NOT NULL,
  `phy_db_name` varchar(128) NOT NULL,
  `group_type` int(11) NOT NULL COMMENT '0:normal group, 1:scaleout group',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_db_grp` (`db_name`,`group_name`),
  KEY `idx_db_type` (`db_name`,`group_type`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `db_info`
--

DROP TABLE IF EXISTS `db_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `db_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `db_name` varchar(128) NOT NULL,
  `app_name` varchar(128) NOT NULL,
  `db_type` int(11) NOT NULL COMMENT '0:part_db,1:default_db,2:system_db,3:cdc_db,4:new_part_db',
  `db_status` int(11) NOT NULL COMMENT '0:running, 1:creating, 2:dropping',
  `charset` varchar(128) DEFAULT NULL,
  `collation` varchar(128) DEFAULT NULL,
  `read_write_status` int(11) NOT NULL DEFAULT '0' COMMENT '0:read_write,1:read_only,2:write_only',
  `extra` text COMMENT 'extra info',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_db` (`db_name`),
  KEY `idx_type` (`db_type`),
  KEY `idx_app_name` (`app_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `db_priv`
--

DROP TABLE IF EXISTS `db_priv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `db_priv` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_name` char(32) NOT NULL DEFAULT '',
  `host` char(60) NOT NULL DEFAULT '',
  `db_name` char(64) NOT NULL DEFAULT '',
  `select_priv` tinyint(1) NOT NULL DEFAULT '0',
  `insert_priv` tinyint(1) NOT NULL DEFAULT '0',
  `update_priv` tinyint(1) NOT NULL DEFAULT '0',
  `delete_priv` tinyint(1) NOT NULL DEFAULT '0',
  `create_priv` tinyint(1) NOT NULL DEFAULT '0',
  `drop_priv` tinyint(1) NOT NULL DEFAULT '0',
  `grant_priv` tinyint(1) NOT NULL DEFAULT '0',
  `index_priv` tinyint(1) NOT NULL DEFAULT '0',
  `alter_priv` tinyint(1) NOT NULL DEFAULT '0',
  `show_view_priv` int(11) NOT NULL DEFAULT '0',
  `create_view_priv` int(11) NOT NULL DEFAULT '0',
  `replication_client_priv` tinyint(1) NOT NULL DEFAULT '0',
  `replication_slave_priv` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk` (`user_name`,`host`,`db_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_engine`
--

DROP TABLE IF EXISTS `ddl_engine`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_engine` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `ddl_type` varchar(64) NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `object_name` varchar(64) NOT NULL,
  `response_node` varchar(64) DEFAULT NULL,
  `execution_node` varchar(64) DEFAULT NULL,
  `state` varchar(64) NOT NULL,
  `resources` text,
  `progress` smallint(6) DEFAULT '0',
  `trace_id` varchar(64) DEFAULT NULL,
  `context` longtext,
  `task_graph` longtext COMMENT 'task graph in agency-list format',
  `result` longtext,
  `ddl_stmt` longtext,
  `gmt_created` bigint(20) unsigned NOT NULL,
  `gmt_modified` bigint(20) unsigned NOT NULL,
  `max_parallelism` smallint(6) NOT NULL DEFAULT '1' COMMENT 'max parallelism for ddl tasks',
  `supported_commands` int(11) NOT NULL COMMENT 'bitmap of supported commands',
  `paused_policy` varchar(64) NOT NULL,
  `rollback_paused_policy` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_id` (`job_id`),
  KEY `schema_name` (`schema_name`,`job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_engine_archive`
--

DROP TABLE IF EXISTS `ddl_engine_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_engine_archive` (
  `id` bigint(20) unsigned NOT NULL,
  `job_id` bigint(20) unsigned NOT NULL,
  `ddl_type` varchar(64) NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `object_name` varchar(64) NOT NULL,
  `response_node` varchar(64) DEFAULT NULL,
  `execution_node` varchar(64) DEFAULT NULL,
  `state` varchar(64) NOT NULL,
  `resources` text,
  `progress` smallint(6) DEFAULT '0',
  `trace_id` varchar(64) DEFAULT NULL,
  `context` longtext,
  `task_graph` longtext COMMENT 'task graph in agency-list format',
  `result` longtext,
  `ddl_stmt` longtext,
  `gmt_created` bigint(20) unsigned NOT NULL,
  `gmt_modified` bigint(20) unsigned NOT NULL,
  `max_parallelism` smallint(6) NOT NULL DEFAULT '1' COMMENT 'max parallelism for ddl tasks',
  `supported_commands` int(11) NOT NULL COMMENT 'bitmap of supported commands',
  `paused_policy` varchar(64) NOT NULL,
  `rollback_paused_policy` varchar(64) NOT NULL,
  KEY `job_id` (`job_id`),
  KEY `schema_name` (`schema_name`,`job_id`),
  KEY `gmt_created` (`gmt_created`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_engine_task`
--

DROP TABLE IF EXISTS `ddl_engine_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_engine_task` (
  `job_id` bigint(20) unsigned NOT NULL,
  `task_id` bigint(20) unsigned NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(256) NOT NULL,
  `state` char(32) NOT NULL DEFAULT 'INIT',
  `exception_action` char(64) NOT NULL DEFAULT 'TRY_RECOVERY_THEN_PAUSE',
  `value` longtext,
  `extra` longtext,
  `cost` longtext,
  `root_job_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`task_id`),
  KEY `job_id` (`job_id`),
  KEY `schema_name` (`schema_name`),
  KEY `root_job_id` (`root_job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_engine_task_archive`
--

DROP TABLE IF EXISTS `ddl_engine_task_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_engine_task_archive` (
  `job_id` bigint(20) unsigned NOT NULL,
  `task_id` bigint(20) unsigned NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(256) NOT NULL,
  `state` char(32) NOT NULL DEFAULT 'INIT',
  `exception_action` char(64) NOT NULL DEFAULT 'TRY_RECOVERY_THEN_PAUSE',
  `value` longtext,
  `extra` longtext,
  `cost` longtext,
  `root_job_id` bigint(20) DEFAULT NULL,
  KEY `job_id_task_id` (`job_id`,`task_id`),
  KEY `task_id_key` (`task_id`),
  KEY `schema_name` (`schema_name`),
  KEY `root_job_id` (`root_job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_jobs`
--

DROP TABLE IF EXISTS `ddl_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_jobs` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(64) NOT NULL,
  `job_id` bigint(20) unsigned NOT NULL,
  `parent_job_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `job_no` smallint(5) unsigned NOT NULL DEFAULT '0',
  `server` varchar(64) NOT NULL,
  `object_schema` varchar(64) NOT NULL,
  `object_name` varchar(64) NOT NULL,
  `new_object_name` varchar(64) NOT NULL,
  `job_type` varchar(64) NOT NULL,
  `phase` varchar(64) NOT NULL,
  `state` varchar(64) NOT NULL,
  `physical_object_done` longtext,
  `progress` smallint(6) NOT NULL DEFAULT '0',
  `ddl_stmt` longtext,
  `old_rule_text` longtext,
  `new_rule_text` longtext,
  `gmt_created` bigint(20) unsigned NOT NULL,
  `gmt_modified` bigint(20) unsigned NOT NULL,
  `remark` longtext,
  `reserved_gsi_int` int(11) DEFAULT NULL,
  `reserved_gsi_txt` longtext,
  `reserved_ddl_int` int(11) DEFAULT NULL,
  `reserved_ddl_txt` longtext,
  `reserved_cmn_int` int(11) DEFAULT NULL,
  `reserved_cmn_txt` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_id` (`job_id`),
  KEY `schema_name` (`schema_name`,`job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ddl_plan`
--

DROP TABLE IF EXISTS `ddl_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ddl_plan` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `plan_id` bigint(20) unsigned NOT NULL,
  `job_id` bigint(20) DEFAULT NULL,
  `table_schema` varchar(64) NOT NULL,
  `ddl_stmt` text NOT NULL,
  `state` char(32) NOT NULL DEFAULT 'INIT',
  `ddl_type` varchar(64) NOT NULL,
  `progress` smallint(6) DEFAULT '0',
  `retry_count` int(11) DEFAULT '0',
  `result` longtext,
  `extras` text,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `resource` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `plan_id` (`plan_id`),
  KEY `table_schema` (`table_schema`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `default_role_state`
--

DROP TABLE IF EXISTS `default_role_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `default_role_state` (
  `account_id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `default_role_state` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`account_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `engines`
--

DROP TABLE IF EXISTS `engines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `engines` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `engine` varchar(64) NOT NULL,
  `support` varchar(8) NOT NULL,
  `comment` varchar(80) DEFAULT NULL,
  `transactions` varchar(3) DEFAULT NULL,
  `xa` varchar(3) DEFAULT NULL,
  `savepoints` varchar(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `feature_usage_statistics`
--

DROP TABLE IF EXISTS `feature_usage_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `feature_usage_statistics` (
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `name` varchar(128) NOT NULL,
  `val` bigint(20) NOT NULL DEFAULT '0',
  `extra` longtext,
  PRIMARY KEY (`name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_storage_backfill_objects`
--

DROP TABLE IF EXISTS `file_storage_backfill_objects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_storage_backfill_objects` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `index_schema` varchar(64) NOT NULL,
  `index_name` varchar(64) NOT NULL,
  `physical_db` varchar(128) DEFAULT NULL COMMENT 'Group key',
  `physical_table` varchar(64) DEFAULT NULL COMMENT 'Physical table name',
  `column_index` bigint(20) NOT NULL DEFAULT '-1' COMMENT 'Column index in index table',
  `parameter_method` varchar(64) DEFAULT NULL COMMENT 'Parameter method for applying LAST_VALUE to extractor',
  `last_value` longtext,
  `max_value` longtext,
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:INIT,1:RUNNING,2:SUCCESS,3:FAILED',
  `message` longtext,
  `success_row_count` bigint(20) unsigned NOT NULL,
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `extra` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_job_db_tb_column` (`job_id`,`physical_db`,`physical_table`,`column_index`),
  KEY `i_job_id` (`job_id`),
  KEY `i_job_id_status` (`job_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_storage_files_meta`
--

DROP TABLE IF EXISTS `file_storage_files_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_storage_files_meta` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(4000) NOT NULL,
  `engine` varchar(255) NOT NULL COMMENT 'engine name',
  `commit_ts` bigint(21) unsigned DEFAULT NULL,
  `remove_ts` bigint(21) unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_commit_ts` (`commit_ts`),
  KEY `idx_remove_ts` (`remove_ts`),
  KEY `file_name` (`file_name`(255))
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_storage_info`
--

DROP TABLE IF EXISTS `file_storage_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_storage_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `inst_id` varchar(128) NOT NULL COMMENT 'polardb-x instance id',
  `engine` varchar(255) NOT NULL COMMENT 'engine name',
  `external_endpoint` varchar(255) NOT NULL COMMENT '外网访问',
  `internal_classic_endpoint` varchar(255) NOT NULL COMMENT 'ECS 的经典网络访问（内网）',
  `internal_vpc_endpoint` varchar(255) NOT NULL COMMENT 'ECS 的 VPC 网络访问（内网）',
  `file_uri` varchar(255) NOT NULL,
  `file_system_conf` text,
  `access_key_id` varchar(255) NOT NULL,
  `access_key_secret` varchar(255) NOT NULL,
  `priority` bigint(11) NOT NULL COMMENT 'the record with the max priority will be chosen to be engine s uri',
  `region_id` varchar(128) DEFAULT NULL,
  `azone_id` varchar(128) DEFAULT NULL,
  `cache_policy` bigint(11) NOT NULL DEFAULT '3' COMMENT 'NO_CACHE(0), META_CACHE(1), DATA_CACHE(2), META_AND_DATA_CACHE(3)',
  `delete_policy` bigint(11) NOT NULL DEFAULT '1' COMMENT '0 for never, 1 for master only, 2 for master and slave',
  `status` bigint(11) NOT NULL DEFAULT '1' COMMENT '0 for disable, 1 for running, 2 for read only',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `endpoint_ordinal` bigint(20) NOT NULL DEFAULT '0' COMMENT '0 use external_endpoint, 1 use internal_classic_endpoint, 2 use internal_vpc_endpoint',
  PRIMARY KEY (`id`),
  UNIQUE KEY `file_uri` (`file_uri`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `files`
--

DROP TABLE IF EXISTS `files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `files` (
  `file_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(4000) DEFAULT NULL,
  `file_type` varchar(20) NOT NULL DEFAULT '',
  `file_meta` longblob,
  `tablespace_name` varchar(64) DEFAULT NULL,
  `table_catalog` varchar(64) NOT NULL DEFAULT '',
  `table_schema` varchar(64) DEFAULT NULL,
  `table_name` varchar(64) DEFAULT NULL,
  `logfile_group_name` varchar(64) DEFAULT NULL,
  `logfile_group_number` bigint(4) DEFAULT NULL,
  `engine` varchar(64) NOT NULL DEFAULT '',
  `fulltext_keys` varchar(64) DEFAULT NULL,
  `deleted_rows` bigint(4) DEFAULT NULL,
  `update_count` bigint(4) DEFAULT NULL,
  `free_extents` bigint(4) DEFAULT NULL,
  `total_extents` bigint(4) DEFAULT NULL,
  `extent_size` bigint(4) NOT NULL DEFAULT '0',
  `initial_size` bigint(21) unsigned DEFAULT NULL,
  `maximum_size` bigint(21) unsigned DEFAULT NULL,
  `autoextend_size` bigint(21) unsigned DEFAULT NULL,
  `creation_time` datetime DEFAULT NULL,
  `last_update_time` datetime DEFAULT NULL,
  `last_access_time` datetime DEFAULT NULL,
  `recover_time` bigint(4) DEFAULT NULL,
  `transaction_counter` bigint(4) DEFAULT NULL,
  `version` bigint(21) unsigned DEFAULT NULL,
  `row_format` varchar(10) DEFAULT NULL,
  `table_rows` bigint(21) unsigned DEFAULT NULL,
  `avg_row_length` bigint(21) unsigned DEFAULT NULL,
  `data_length` bigint(21) unsigned DEFAULT NULL,
  `max_data_length` bigint(21) unsigned DEFAULT NULL,
  `index_length` bigint(21) unsigned DEFAULT NULL,
  `data_free` bigint(21) unsigned DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `check_time` datetime DEFAULT NULL,
  `checksum` bigint(21) unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '',
  `extra` varchar(255) DEFAULT NULL,
  `task_id` bigint(21) unsigned NOT NULL,
  `life_cycle` bigint(21) unsigned NOT NULL COMMENT '0: creating, 1: ready, 2: deleted',
  `local_path` varchar(4000) DEFAULT NULL COMMENT 'local path of orc file',
  `logical_schema_name` varchar(255) NOT NULL,
  `logical_table_name` varchar(255) NOT NULL,
  `commit_ts` bigint(21) unsigned DEFAULT NULL,
  `remove_ts` bigint(21) unsigned DEFAULT NULL,
  `file_hash` bigint(21) DEFAULT NULL,
  `local_partition_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`file_id`),
  KEY `table_index` (`table_schema`,`table_name`),
  KEY `task_id` (`task_id`),
  KEY `file_name` (`file_name`(255)),
  KEY `logical_name` (`logical_schema_name`,`logical_table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fired_scheduled_jobs`
--

DROP TABLE IF EXISTS `fired_scheduled_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `fired_scheduled_jobs` (
  `schedule_id` bigint(20) NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) DEFAULT NULL,
  `fire_time` bigint(20) NOT NULL DEFAULT '0',
  `start_time` bigint(20) NOT NULL DEFAULT '0',
  `finish_time` bigint(20) NOT NULL DEFAULT '0',
  `state` varchar(64) NOT NULL COMMENT 'QUEUED/RUNNING/SUCCESS/FAILED/SKIPPED',
  `remark` text,
  `result` longtext,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_group_name` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`schedule_id`,`fire_time`),
  KEY `table_schema` (`table_schema`,`table_name`),
  KEY `fire_time` (`fire_time`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `global_variables`
--

DROP TABLE IF EXISTS `global_variables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `global_variables` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `variable_name` varchar(64) NOT NULL,
  `variable_value` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `group_detail_info`
--

DROP TABLE IF EXISTS `group_detail_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `group_detail_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `db_name` varchar(128) NOT NULL,
  `group_name` varchar(128) NOT NULL,
  `storage_inst_id` varchar(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_db_grp` (`inst_id`,`db_name`,`group_name`),
  KEY `idx_storage_inst` (`storage_inst_id`,`inst_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `indexes`
--

DROP TABLE IF EXISTS `indexes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `indexes` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `non_unique` bigint(1) NOT NULL DEFAULT '0',
  `index_schema` varchar(64) NOT NULL,
  `index_name` varchar(64) NOT NULL,
  `seq_in_index` bigint(10) NOT NULL DEFAULT '0',
  `column_name` varchar(64) NOT NULL,
  `collation` varchar(3) DEFAULT NULL,
  `cardinality` bigint(20) DEFAULT NULL,
  `sub_part` bigint(3) DEFAULT NULL,
  `packed` varchar(10) DEFAULT NULL,
  `nullable` varchar(3) DEFAULT NULL,
  `index_type` varchar(16) DEFAULT NULL COMMENT 'BTREE, FULLTEXT, HASH, RTREE, GLOBAL',
  `comment` varchar(16) DEFAULT NULL COMMENT 'INDEX, COVERING',
  `index_comment` varchar(1024) DEFAULT NULL,
  `index_column_type` bigint(10) DEFAULT '0' COMMENT '0:INDEX,1:COVERING',
  `index_location` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:LOCAL,1:GLOBAL',
  `index_table_name` varchar(64) DEFAULT NULL,
  `index_status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:CREATING,1:DELETE_ONLY,2:WRITE_ONLY,3:WRITE_REORG,4:PUBLIC,5:DELETE_REORG,6:REMOVING,7:ABSENT',
  `version` bigint(20) NOT NULL DEFAULT '1',
  `flag` bigint(20) unsigned DEFAULT '0',
  `visible` bigint(20) NOT NULL DEFAULT '0' COMMENT '0:VISIBLE,1:INVISIBLE',
  `visit_frequency` bigint(20) unsigned NOT NULL DEFAULT '0',
  `last_access_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_schema` (`table_schema`,`table_name`,`index_name`,`column_name`),
  KEY `i_index_name_version` (`index_name`,`version`),
  KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inst_config`
--

DROP TABLE IF EXISTS `inst_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `inst_config` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `param_key` varchar(128) NOT NULL,
  `param_val` varchar(1024) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_id_key` (`inst_id`,`param_key`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inst_lock`
--

DROP TABLE IF EXISTS `inst_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `inst_lock` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `locked` int(11) NOT NULL COMMENT '0:unlocked, 1:locked',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_id` (`inst_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `java_functions`
--

DROP TABLE IF EXISTS `java_functions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `java_functions` (
  `function_name` varchar(64) NOT NULL,
  `class_name` varchar(64) NOT NULL,
  `code` text NOT NULL,
  `code_language` varchar(10) NOT NULL DEFAULT 'java',
  `input_types` varchar(512) NOT NULL,
  `return_type` varchar(32) NOT NULL,
  `is_no_state` tinyint(1) NOT NULL DEFAULT '0',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`function_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `join_group_info`
--

DROP TABLE IF EXISTS `join_group_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `join_group_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `join_group_name` varchar(64) NOT NULL,
  `locality` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uidx_sch_jgname` (`table_schema`,`join_group_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `join_group_table_detail`
--

DROP TABLE IF EXISTS `join_group_table_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `join_group_table_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `join_group_id` bigint(20) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uidx_sch_tbname` (`table_schema`,`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `k8s_topology`
--

DROP TABLE IF EXISTS `k8s_topology`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `k8s_topology` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `uid` varchar(128) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(128) COLLATE utf8_unicode_ci NOT NULL,
  `type` varchar(10) COLLATE utf8_unicode_ci NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uid` (`uid`),
  UNIQUE KEY `name` (`name`,`type`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `key_column_usage`
--

DROP TABLE IF EXISTS `key_column_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `key_column_usage` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `constraint_catalog` varchar(512) DEFAULT NULL,
  `constraint_schema` varchar(64) NOT NULL,
  `constraint_name` varchar(64) NOT NULL,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(64) NOT NULL,
  `ordinal_position` bigint(10) NOT NULL DEFAULT '0',
  `position_in_unique_constraint` bigint(10) DEFAULT NULL,
  `referenced_table_schema` varchar(64) DEFAULT NULL,
  `referenced_table_name` varchar(64) DEFAULT NULL,
  `referenced_column_name` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `constraint_schema` (`constraint_schema`,`constraint_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lease`
--

DROP TABLE IF EXISTS `lease`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lease` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(255) NOT NULL,
  `lease_holder` varchar(255) NOT NULL,
  `lease_key` varchar(255) NOT NULL,
  `start_at` bigint(20) NOT NULL,
  `last_modified` bigint(20) NOT NULL,
  `expire_at` bigint(20) NOT NULL,
  `ttl_millis` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `lease_key` (`lease_key`),
  KEY `expire_at` (`expire_at`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `locality_info`
--

DROP TABLE IF EXISTS `locality_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `locality_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `object_type` int(11) NOT NULL COMMENT '0:default, 1:database, 2:tablegroup, 3:partitiongroup',
  `object_id` bigint(20) NOT NULL COMMENT 'id of the locality object',
  `primary_zone` varchar(128) NOT NULL DEFAULT '' COMMENT 'primary zone of the object',
  `locality` text NOT NULL COMMENT 'locality of the object',
  PRIMARY KEY (`id`),
  UNIQUE KEY `object_type` (`object_type`,`object_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ndv_sketch_statistics`
--

DROP TABLE IF EXISTS `ndv_sketch_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ndv_sketch_statistics` (
  `schema_name` varchar(64) NOT NULL DEFAULT '',
  `table_name` varchar(64) NOT NULL,
  `column_names` varchar(128) NOT NULL,
  `shard_part` varchar(255) NOT NULL,
  `dn_cardinality` bigint(20) NOT NULL,
  `composite_cardinality` bigint(20) NOT NULL,
  `sketch_bytes` varbinary(12288) NOT NULL,
  `sketch_type` varchar(16) NOT NULL,
  `compress_type` varchar(16) NOT NULL DEFAULT 'NA',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`schema_name`,`table_name`,`column_names`,`shard_part`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `node_info`
--

DROP TABLE IF EXISTS `node_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `node_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `cluster` varchar(64) NOT NULL DEFAULT '',
  `inst_id` varchar(128) NOT NULL,
  `nodeid` varchar(64) NOT NULL DEFAULT '',
  `version` varchar(64) NOT NULL DEFAULT '',
  `ip` varchar(20) NOT NULL DEFAULT '',
  `port` int(11) NOT NULL,
  `rpc_port` bigint(10) NOT NULL DEFAULT '0',
  `role` bigint(10) NOT NULL DEFAULT '0',
  `status` bigint(10) NOT NULL DEFAULT '1',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `cluster` (`cluster`,`nodeid`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `parameters`
--

DROP TABLE IF EXISTS `parameters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `parameters` (
  `SPECIFIC_CATALOG` varchar(512) NOT NULL DEFAULT '',
  `SPECIFIC_SCHEMA` varchar(64) NOT NULL DEFAULT '',
  `SPECIFIC_NAME` varchar(64) NOT NULL DEFAULT '',
  `ORDINAL_POSITION` int(21) NOT NULL DEFAULT '0',
  `PARAMETER_MODE` varchar(5) DEFAULT NULL,
  `PARAMETER_NAME` varchar(64) DEFAULT NULL,
  `DATA_TYPE` varchar(64) NOT NULL DEFAULT '',
  `CHARACTER_MAXIMUM_LENGTH` int(21) DEFAULT NULL,
  `CHARACTER_OCTET_LENGTH` int(21) DEFAULT NULL,
  `NUMERIC_PRECISION` bigint(21) unsigned DEFAULT NULL,
  `NUMERIC_SCALE` int(21) DEFAULT NULL,
  `DATETIME_PRECISION` bigint(21) unsigned DEFAULT NULL,
  `CHARACTER_SET_NAME` varchar(64) DEFAULT NULL,
  `COLLATION_NAME` varchar(64) DEFAULT NULL,
  `DTD_IDENTIFIER` longtext NOT NULL,
  `ROUTINE_TYPE` varchar(9) NOT NULL DEFAULT '',
  UNIQUE KEY `u_schema_name` (`SPECIFIC_SCHEMA`,`SPECIFIC_NAME`,`PARAMETER_NAME`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `partition_group`
--

DROP TABLE IF EXISTS `partition_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `partition_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `partition_name` varchar(64) NOT NULL,
  `tg_id` bigint(20) NOT NULL,
  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `phy_db` varchar(64) NOT NULL,
  `locality` text,
  `primary_zone` text,
  `pax_group_id` bigint(20) NOT NULL DEFAULT '-1',
  `meta_version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `visible` int(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `u_pname` (`tg_id`,`partition_name`,`meta_version`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `partition_group_delta`
--

DROP TABLE IF EXISTS `partition_group_delta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `partition_group_delta` (
  `id` bigint(20) NOT NULL DEFAULT '0',
  `partition_name` varchar(64) NOT NULL,
  `tg_id` bigint(20) NOT NULL,
  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `phy_db` varchar(64) NOT NULL,
  `locality` text,
  `primary_zone` text,
  `pax_group_id` bigint(20) NOT NULL DEFAULT '-1',
  `meta_version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `type` int(11) NOT NULL DEFAULT '1' COMMENT '1:new partition group 0:outdated partition group',
  `visible` int(11) NOT NULL DEFAULT '1',
  UNIQUE KEY `u_pname` (`tg_id`,`partition_name`,`type`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `partitions`
--

DROP TABLE IF EXISTS `partitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `partitions` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `partition_name` varchar(64) DEFAULT NULL,
  `subpartition_name` varchar(64) DEFAULT NULL,
  `partition_ordinal_position` bigint(20) unsigned DEFAULT NULL,
  `subpartition_ordinal_position` bigint(20) unsigned DEFAULT NULL,
  `partition_method` varchar(18) DEFAULT NULL,
  `subpartition_method` varchar(12) DEFAULT NULL,
  `partition_expression` longtext,
  `subpartition_expression` longtext,
  `partition_description` longtext,
  `table_rows` bigint(20) unsigned NOT NULL DEFAULT '0',
  `avg_row_length` bigint(20) unsigned NOT NULL DEFAULT '0',
  `data_length` bigint(20) unsigned NOT NULL DEFAULT '0',
  `max_data_length` bigint(20) unsigned DEFAULT NULL,
  `index_length` bigint(20) unsigned NOT NULL DEFAULT '0',
  `data_free` bigint(20) unsigned NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `check_time` datetime DEFAULT NULL,
  `checksum` bigint(20) unsigned DEFAULT NULL,
  `partition_comment` varchar(80) DEFAULT NULL,
  `nodegroup` varchar(12) DEFAULT NULL,
  `tablespace_name` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `table_schema` (`table_schema`,`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `partitions_heatmap`
--

DROP TABLE IF EXISTS `partitions_heatmap`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `partitions_heatmap` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `layer_num` tinyint(3) unsigned NOT NULL,
  `timestamp` bigint(20) unsigned NOT NULL,
  `axis` text,
  PRIMARY KEY (`id`),
  KEY `layer_num` (`layer_num`,`timestamp`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `plan_info`
--

DROP TABLE IF EXISTS `plan_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plan_info` (
  `id` bigint(20) NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  `baseline_id` bigint(20) NOT NULL,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_execute_time` timestamp NULL DEFAULT NULL,
  `plan` longtext NOT NULL,
  `plan_type` varchar(255) DEFAULT NULL,
  `plan_error` longtext,
  `choose_count` bigint(20) NOT NULL,
  `cost` double NOT NULL,
  `estimate_execution_time` double NOT NULL,
  `accepted` tinyint(4) NOT NULL,
  `fixed` tinyint(4) NOT NULL,
  `trace_id` varchar(255) NOT NULL,
  `origin` varchar(255) DEFAULT NULL,
  `estimate_optimize_time` double DEFAULT NULL,
  `cpu` double DEFAULT NULL,
  `memory` double DEFAULT NULL,
  `io` double DEFAULT NULL,
  `net` double DEFAULT NULL,
  `extend_field` longtext COMMENT 'Json string extend field',
  `TABLES_HASHCODE` bigint(20) NOT NULL,
  PRIMARY KEY (`schema_name`,`id`,`baseline_id`),
  KEY `baseline_id_key` (`schema_name`,`baseline_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `polardbx_extra`
--

DROP TABLE IF EXISTS `polardbx_extra`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `polardbx_extra` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `inst_id` varchar(128) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(128) COLLATE utf8_unicode_ci NOT NULL,
  `type` varchar(10) COLLATE utf8_unicode_ci NOT NULL,
  `comment` varchar(256) COLLATE utf8_unicode_ci NOT NULL,
  `status` int(4) NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_id_name_type` (`inst_id`,`name`,`type`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_004b896531f6e456e5378e160615d65a`
--

DROP TABLE IF EXISTS `pxc_seq_004b896531f6e456e5378e160615d65a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_004b896531f6e456e5378e160615d65a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_028ea005313ef6ebfe356f2ed11e5849`
--

DROP TABLE IF EXISTS `pxc_seq_028ea005313ef6ebfe356f2ed11e5849`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_028ea005313ef6ebfe356f2ed11e5849` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_02abdf118259ced051bf19eb078a6c54`
--

DROP TABLE IF EXISTS `pxc_seq_02abdf118259ced051bf19eb078a6c54`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_02abdf118259ced051bf19eb078a6c54` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_02d02c23acb1733f5d24ddae12dab6ae`
--

DROP TABLE IF EXISTS `pxc_seq_02d02c23acb1733f5d24ddae12dab6ae`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_02d02c23acb1733f5d24ddae12dab6ae` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0414af1b031c3d33505d11162e3352cc`
--

DROP TABLE IF EXISTS `pxc_seq_0414af1b031c3d33505d11162e3352cc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0414af1b031c3d33505d11162e3352cc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_05de8be8a37710d9e25591f9145c5394`
--

DROP TABLE IF EXISTS `pxc_seq_05de8be8a37710d9e25591f9145c5394`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_05de8be8a37710d9e25591f9145c5394` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_061661fda3bb40583a13ba26f7a79cdf`
--

DROP TABLE IF EXISTS `pxc_seq_061661fda3bb40583a13ba26f7a79cdf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_061661fda3bb40583a13ba26f7a79cdf` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0759afc8022bb2a246d465d5d6c85d0b`
--

DROP TABLE IF EXISTS `pxc_seq_0759afc8022bb2a246d465d5d6c85d0b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0759afc8022bb2a246d465d5d6c85d0b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0a2f4b80d765422f7a8fa024c9c1a105`
--

DROP TABLE IF EXISTS `pxc_seq_0a2f4b80d765422f7a8fa024c9c1a105`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0a2f4b80d765422f7a8fa024c9c1a105` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0b4c6b11ba74957d4422e1ffede807a5`
--

DROP TABLE IF EXISTS `pxc_seq_0b4c6b11ba74957d4422e1ffede807a5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0b4c6b11ba74957d4422e1ffede807a5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0cd11ba017f4d3fcf5140115432b8e9a`
--

DROP TABLE IF EXISTS `pxc_seq_0cd11ba017f4d3fcf5140115432b8e9a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0cd11ba017f4d3fcf5140115432b8e9a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0d0abfc4979e12c2a72db4580f3a9a34`
--

DROP TABLE IF EXISTS `pxc_seq_0d0abfc4979e12c2a72db4580f3a9a34`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0d0abfc4979e12c2a72db4580f3a9a34` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0d4a5917ca97380e2df5ec40af67e89d`
--

DROP TABLE IF EXISTS `pxc_seq_0d4a5917ca97380e2df5ec40af67e89d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0d4a5917ca97380e2df5ec40af67e89d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0d97a73f265c31ed1ed0cf615f0eabc1`
--

DROP TABLE IF EXISTS `pxc_seq_0d97a73f265c31ed1ed0cf615f0eabc1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0d97a73f265c31ed1ed0cf615f0eabc1` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0dda89e416c05bd76089ff2101442c5f`
--

DROP TABLE IF EXISTS `pxc_seq_0dda89e416c05bd76089ff2101442c5f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0dda89e416c05bd76089ff2101442c5f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0e524c423dd9086707be152426ca8fbf`
--

DROP TABLE IF EXISTS `pxc_seq_0e524c423dd9086707be152426ca8fbf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0e524c423dd9086707be152426ca8fbf` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_0fae060049ce082372d805c84422997f`
--

DROP TABLE IF EXISTS `pxc_seq_0fae060049ce082372d805c84422997f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_0fae060049ce082372d805c84422997f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_10042be44dd4720a25083bbef8d64a72`
--

DROP TABLE IF EXISTS `pxc_seq_10042be44dd4720a25083bbef8d64a72`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_10042be44dd4720a25083bbef8d64a72` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_11313150f01bddc1eb3d10cb30fdb169`
--

DROP TABLE IF EXISTS `pxc_seq_11313150f01bddc1eb3d10cb30fdb169`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_11313150f01bddc1eb3d10cb30fdb169` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_11658271fbfe16b46b0d5a3a40e2882a`
--

DROP TABLE IF EXISTS `pxc_seq_11658271fbfe16b46b0d5a3a40e2882a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_11658271fbfe16b46b0d5a3a40e2882a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_11ad084b195aac783f83084c1e90a371`
--

DROP TABLE IF EXISTS `pxc_seq_11ad084b195aac783f83084c1e90a371`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_11ad084b195aac783f83084c1e90a371` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_13117b680bbeed45fbeaacae52705bc1`
--

DROP TABLE IF EXISTS `pxc_seq_13117b680bbeed45fbeaacae52705bc1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_13117b680bbeed45fbeaacae52705bc1` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_137e3df7c1cb2efa6d3462afb01eaeae`
--

DROP TABLE IF EXISTS `pxc_seq_137e3df7c1cb2efa6d3462afb01eaeae`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_137e3df7c1cb2efa6d3462afb01eaeae` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_1762e1491b25336ade9e009cf7cdefbd`
--

DROP TABLE IF EXISTS `pxc_seq_1762e1491b25336ade9e009cf7cdefbd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_1762e1491b25336ade9e009cf7cdefbd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_1a742ae97300562c620a272eb26f0a3a`
--

DROP TABLE IF EXISTS `pxc_seq_1a742ae97300562c620a272eb26f0a3a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_1a742ae97300562c620a272eb26f0a3a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_1b354ecb9a2949ec8037a42a3245e554`
--

DROP TABLE IF EXISTS `pxc_seq_1b354ecb9a2949ec8037a42a3245e554`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_1b354ecb9a2949ec8037a42a3245e554` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_1c3eb0bb8b5a03fd74a5ec9686320266`
--

DROP TABLE IF EXISTS `pxc_seq_1c3eb0bb8b5a03fd74a5ec9686320266`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_1c3eb0bb8b5a03fd74a5ec9686320266` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_20db806fae79905d67afd488d59081ed`
--

DROP TABLE IF EXISTS `pxc_seq_20db806fae79905d67afd488d59081ed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_20db806fae79905d67afd488d59081ed` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_20e3a34961484892d5c2fe2b4033e370`
--

DROP TABLE IF EXISTS `pxc_seq_20e3a34961484892d5c2fe2b4033e370`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_20e3a34961484892d5c2fe2b4033e370` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2112ad332f4503ce28e0c084059f0d9a`
--

DROP TABLE IF EXISTS `pxc_seq_2112ad332f4503ce28e0c084059f0d9a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2112ad332f4503ce28e0c084059f0d9a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_22be21a38ae18748e98b78ce30ebdbe3`
--

DROP TABLE IF EXISTS `pxc_seq_22be21a38ae18748e98b78ce30ebdbe3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_22be21a38ae18748e98b78ce30ebdbe3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_22c8c8d3d90fe871afa29198f01f1495`
--

DROP TABLE IF EXISTS `pxc_seq_22c8c8d3d90fe871afa29198f01f1495`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_22c8c8d3d90fe871afa29198f01f1495` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2312dfbcb3f127b4507d5ecabcdc8188`
--

DROP TABLE IF EXISTS `pxc_seq_2312dfbcb3f127b4507d5ecabcdc8188`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2312dfbcb3f127b4507d5ecabcdc8188` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_232b0d5aaeaf212eaf42e2d80fd7d204`
--

DROP TABLE IF EXISTS `pxc_seq_232b0d5aaeaf212eaf42e2d80fd7d204`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_232b0d5aaeaf212eaf42e2d80fd7d204` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2365c500fffb1a3f52daf7dfad4e670c`
--

DROP TABLE IF EXISTS `pxc_seq_2365c500fffb1a3f52daf7dfad4e670c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2365c500fffb1a3f52daf7dfad4e670c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2367151cb76ac8f052a8074d0a0c7d3a`
--

DROP TABLE IF EXISTS `pxc_seq_2367151cb76ac8f052a8074d0a0c7d3a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2367151cb76ac8f052a8074d0a0c7d3a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_24c101c6b58a5f65ff24303ce91a6c03`
--

DROP TABLE IF EXISTS `pxc_seq_24c101c6b58a5f65ff24303ce91a6c03`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_24c101c6b58a5f65ff24303ce91a6c03` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_25783356aa13a983e673f4536841167e`
--

DROP TABLE IF EXISTS `pxc_seq_25783356aa13a983e673f4536841167e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_25783356aa13a983e673f4536841167e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_27395e19c2e5b2cd599a6e2b8c80ce79`
--

DROP TABLE IF EXISTS `pxc_seq_27395e19c2e5b2cd599a6e2b8c80ce79`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_27395e19c2e5b2cd599a6e2b8c80ce79` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_27db5f0d7d4ed76afac35586d91f64c7`
--

DROP TABLE IF EXISTS `pxc_seq_27db5f0d7d4ed76afac35586d91f64c7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_27db5f0d7d4ed76afac35586d91f64c7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_28d11bf5a92ee30f6f9a623bb71786aa`
--

DROP TABLE IF EXISTS `pxc_seq_28d11bf5a92ee30f6f9a623bb71786aa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_28d11bf5a92ee30f6f9a623bb71786aa` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_29dfe1e15e3f2fab423eca74040bd7b6`
--

DROP TABLE IF EXISTS `pxc_seq_29dfe1e15e3f2fab423eca74040bd7b6`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_29dfe1e15e3f2fab423eca74040bd7b6` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2a84f315b044a9cb4fa6ae5b34d7b9dd`
--

DROP TABLE IF EXISTS `pxc_seq_2a84f315b044a9cb4fa6ae5b34d7b9dd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2a84f315b044a9cb4fa6ae5b34d7b9dd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2ae7ea1ac2eb4e5dcfc443da1b8a6cfd`
--

DROP TABLE IF EXISTS `pxc_seq_2ae7ea1ac2eb4e5dcfc443da1b8a6cfd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2ae7ea1ac2eb4e5dcfc443da1b8a6cfd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2b0f8e16a62f9490519620860c1497b0`
--

DROP TABLE IF EXISTS `pxc_seq_2b0f8e16a62f9490519620860c1497b0`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2b0f8e16a62f9490519620860c1497b0` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2be5f86dd42f5e78928971a9ac0c87af`
--

DROP TABLE IF EXISTS `pxc_seq_2be5f86dd42f5e78928971a9ac0c87af`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2be5f86dd42f5e78928971a9ac0c87af` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2c1a30767ae47fc97e1dfc09a378398b`
--

DROP TABLE IF EXISTS `pxc_seq_2c1a30767ae47fc97e1dfc09a378398b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2c1a30767ae47fc97e1dfc09a378398b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2cc9e5f611dd5d96c56ed5579e850eb5`
--

DROP TABLE IF EXISTS `pxc_seq_2cc9e5f611dd5d96c56ed5579e850eb5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2cc9e5f611dd5d96c56ed5579e850eb5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2d14a615d931182267205454d5b02c7b`
--

DROP TABLE IF EXISTS `pxc_seq_2d14a615d931182267205454d5b02c7b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2d14a615d931182267205454d5b02c7b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2d8643524174f297c0bbfd6211c7473e`
--

DROP TABLE IF EXISTS `pxc_seq_2d8643524174f297c0bbfd6211c7473e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2d8643524174f297c0bbfd6211c7473e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2db39a1d7960f7317fcafea4c5f66936`
--

DROP TABLE IF EXISTS `pxc_seq_2db39a1d7960f7317fcafea4c5f66936`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2db39a1d7960f7317fcafea4c5f66936` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2e5934708784df4de990dd31a6bf062c`
--

DROP TABLE IF EXISTS `pxc_seq_2e5934708784df4de990dd31a6bf062c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2e5934708784df4de990dd31a6bf062c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_2fa436e2407a7996e54715ef4ff7a941`
--

DROP TABLE IF EXISTS `pxc_seq_2fa436e2407a7996e54715ef4ff7a941`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_2fa436e2407a7996e54715ef4ff7a941` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_30ee2c808ec8e63a22497cfb131a33d7`
--

DROP TABLE IF EXISTS `pxc_seq_30ee2c808ec8e63a22497cfb131a33d7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_30ee2c808ec8e63a22497cfb131a33d7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_31e465d0d5f703d407cc195a5470fa4a`
--

DROP TABLE IF EXISTS `pxc_seq_31e465d0d5f703d407cc195a5470fa4a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_31e465d0d5f703d407cc195a5470fa4a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_33b80664966c70e3372c341dd0dbdeae`
--

DROP TABLE IF EXISTS `pxc_seq_33b80664966c70e3372c341dd0dbdeae`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_33b80664966c70e3372c341dd0dbdeae` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3474ec6c01131c181d7a9ccf89004a0c`
--

DROP TABLE IF EXISTS `pxc_seq_3474ec6c01131c181d7a9ccf89004a0c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3474ec6c01131c181d7a9ccf89004a0c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_34f4169991b4331cca12a0fe17c29279`
--

DROP TABLE IF EXISTS `pxc_seq_34f4169991b4331cca12a0fe17c29279`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_34f4169991b4331cca12a0fe17c29279` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_35b5aa2d344a782120ef72aa3626f824`
--

DROP TABLE IF EXISTS `pxc_seq_35b5aa2d344a782120ef72aa3626f824`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_35b5aa2d344a782120ef72aa3626f824` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_36f1e00f0facbe5ab4f3dd37c1c95c10`
--

DROP TABLE IF EXISTS `pxc_seq_36f1e00f0facbe5ab4f3dd37c1c95c10`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_36f1e00f0facbe5ab4f3dd37c1c95c10` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_36f71c0f12fdd05cddbf278ec896e34d`
--

DROP TABLE IF EXISTS `pxc_seq_36f71c0f12fdd05cddbf278ec896e34d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_36f71c0f12fdd05cddbf278ec896e34d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3928dea45b26e55c176b551b7d0843ee`
--

DROP TABLE IF EXISTS `pxc_seq_3928dea45b26e55c176b551b7d0843ee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3928dea45b26e55c176b551b7d0843ee` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3a9b887b5751493562337e1ac55a0df1`
--

DROP TABLE IF EXISTS `pxc_seq_3a9b887b5751493562337e1ac55a0df1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3a9b887b5751493562337e1ac55a0df1` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3ab6a9bf6f2396141677b016413ae05d`
--

DROP TABLE IF EXISTS `pxc_seq_3ab6a9bf6f2396141677b016413ae05d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3ab6a9bf6f2396141677b016413ae05d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3c41b4574cfcdc8c2251fd219e34b659`
--

DROP TABLE IF EXISTS `pxc_seq_3c41b4574cfcdc8c2251fd219e34b659`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3c41b4574cfcdc8c2251fd219e34b659` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3dc36652482458b98ab67e03da3d9d5f`
--

DROP TABLE IF EXISTS `pxc_seq_3dc36652482458b98ab67e03da3d9d5f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3dc36652482458b98ab67e03da3d9d5f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3e0d106d800902335086ed2e62f04938`
--

DROP TABLE IF EXISTS `pxc_seq_3e0d106d800902335086ed2e62f04938`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3e0d106d800902335086ed2e62f04938` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_3e8c86a326cb4817c18626e365b95853`
--

DROP TABLE IF EXISTS `pxc_seq_3e8c86a326cb4817c18626e365b95853`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_3e8c86a326cb4817c18626e365b95853` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4288fa29a5cb9cd58f909088ca38341b`
--

DROP TABLE IF EXISTS `pxc_seq_4288fa29a5cb9cd58f909088ca38341b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4288fa29a5cb9cd58f909088ca38341b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_42f2998f8a0d11bd11988b26dc6208c7`
--

DROP TABLE IF EXISTS `pxc_seq_42f2998f8a0d11bd11988b26dc6208c7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_42f2998f8a0d11bd11988b26dc6208c7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_43654183b66b64bb121dc867343f4c15`
--

DROP TABLE IF EXISTS `pxc_seq_43654183b66b64bb121dc867343f4c15`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_43654183b66b64bb121dc867343f4c15` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4443d8ff0d7911426cac67a175c50f97`
--

DROP TABLE IF EXISTS `pxc_seq_4443d8ff0d7911426cac67a175c50f97`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4443d8ff0d7911426cac67a175c50f97` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_466e0fedec92a79bb22e1b82ede12810`
--

DROP TABLE IF EXISTS `pxc_seq_466e0fedec92a79bb22e1b82ede12810`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_466e0fedec92a79bb22e1b82ede12810` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_47c7eecfe31e8b30c7386d697c9647b5`
--

DROP TABLE IF EXISTS `pxc_seq_47c7eecfe31e8b30c7386d697c9647b5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_47c7eecfe31e8b30c7386d697c9647b5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4838e2098afe2e75e603f718b0ab1311`
--

DROP TABLE IF EXISTS `pxc_seq_4838e2098afe2e75e603f718b0ab1311`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4838e2098afe2e75e603f718b0ab1311` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_49753f2ce7ce903e2975b7594e86b629`
--

DROP TABLE IF EXISTS `pxc_seq_49753f2ce7ce903e2975b7594e86b629`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_49753f2ce7ce903e2975b7594e86b629` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_49bf5693dc5eedcafc6ec2753fc3444f`
--

DROP TABLE IF EXISTS `pxc_seq_49bf5693dc5eedcafc6ec2753fc3444f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_49bf5693dc5eedcafc6ec2753fc3444f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_49ca7b30770d75f4f00b401b2aa016da`
--

DROP TABLE IF EXISTS `pxc_seq_49ca7b30770d75f4f00b401b2aa016da`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_49ca7b30770d75f4f00b401b2aa016da` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4a27c078c609b42558e1118e806972d5`
--

DROP TABLE IF EXISTS `pxc_seq_4a27c078c609b42558e1118e806972d5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4a27c078c609b42558e1118e806972d5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4b8923824743e6316a74572b8461e200`
--

DROP TABLE IF EXISTS `pxc_seq_4b8923824743e6316a74572b8461e200`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4b8923824743e6316a74572b8461e200` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4c360a15525d0f529c6e2bdc37a8e3a8`
--

DROP TABLE IF EXISTS `pxc_seq_4c360a15525d0f529c6e2bdc37a8e3a8`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4c360a15525d0f529c6e2bdc37a8e3a8` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4d75fe7de919502c1abcfadd4a9d524d`
--

DROP TABLE IF EXISTS `pxc_seq_4d75fe7de919502c1abcfadd4a9d524d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4d75fe7de919502c1abcfadd4a9d524d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_4e347ab9cc7c5cf1e7250ee359fe0e66`
--

DROP TABLE IF EXISTS `pxc_seq_4e347ab9cc7c5cf1e7250ee359fe0e66`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_4e347ab9cc7c5cf1e7250ee359fe0e66` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_501e743aba103a8d10fb8bdbbf44b6f2`
--

DROP TABLE IF EXISTS `pxc_seq_501e743aba103a8d10fb8bdbbf44b6f2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_501e743aba103a8d10fb8bdbbf44b6f2` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_50cf03b65cd5b9a5ea6e1ce58acd5fe4`
--

DROP TABLE IF EXISTS `pxc_seq_50cf03b65cd5b9a5ea6e1ce58acd5fe4`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_50cf03b65cd5b9a5ea6e1ce58acd5fe4` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_50de50e298800d246402662c52410f61`
--

DROP TABLE IF EXISTS `pxc_seq_50de50e298800d246402662c52410f61`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_50de50e298800d246402662c52410f61` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5147f7e97be9afc289c91e0ff38f5114`
--

DROP TABLE IF EXISTS `pxc_seq_5147f7e97be9afc289c91e0ff38f5114`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5147f7e97be9afc289c91e0ff38f5114` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_534750ff1fe3412d44ed32eb69c5b179`
--

DROP TABLE IF EXISTS `pxc_seq_534750ff1fe3412d44ed32eb69c5b179`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_534750ff1fe3412d44ed32eb69c5b179` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_54d41b7b7612e2f08f4abb19579f87d0`
--

DROP TABLE IF EXISTS `pxc_seq_54d41b7b7612e2f08f4abb19579f87d0`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_54d41b7b7612e2f08f4abb19579f87d0` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_550ec48de76c878e1d3224ad833bbc3a`
--

DROP TABLE IF EXISTS `pxc_seq_550ec48de76c878e1d3224ad833bbc3a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_550ec48de76c878e1d3224ad833bbc3a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_57250982b9b942ff2dec2d280e2a8e25`
--

DROP TABLE IF EXISTS `pxc_seq_57250982b9b942ff2dec2d280e2a8e25`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_57250982b9b942ff2dec2d280e2a8e25` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_57cfb4f837ade74bbc2796ced14bdadc`
--

DROP TABLE IF EXISTS `pxc_seq_57cfb4f837ade74bbc2796ced14bdadc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_57cfb4f837ade74bbc2796ced14bdadc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_57f337a295e0abfb1c6fa0a794b3e146`
--

DROP TABLE IF EXISTS `pxc_seq_57f337a295e0abfb1c6fa0a794b3e146`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_57f337a295e0abfb1c6fa0a794b3e146` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_595837f3cb97f714c4581c33e5e71b1c`
--

DROP TABLE IF EXISTS `pxc_seq_595837f3cb97f714c4581c33e5e71b1c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_595837f3cb97f714c4581c33e5e71b1c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_59b2a575e35fa6022cf404ec2c5b4bd9`
--

DROP TABLE IF EXISTS `pxc_seq_59b2a575e35fa6022cf404ec2c5b4bd9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_59b2a575e35fa6022cf404ec2c5b4bd9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5ab4c44e75d8cf0a8dca4ea2c09a2abc`
--

DROP TABLE IF EXISTS `pxc_seq_5ab4c44e75d8cf0a8dca4ea2c09a2abc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5ab4c44e75d8cf0a8dca4ea2c09a2abc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5b26c3da1e19c87c2b07af51fbbead77`
--

DROP TABLE IF EXISTS `pxc_seq_5b26c3da1e19c87c2b07af51fbbead77`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5b26c3da1e19c87c2b07af51fbbead77` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5c045198efcbc9fde6dc1c83a501ca25`
--

DROP TABLE IF EXISTS `pxc_seq_5c045198efcbc9fde6dc1c83a501ca25`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5c045198efcbc9fde6dc1c83a501ca25` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5ca53be3017fc5154e3fc9419e9a9d9b`
--

DROP TABLE IF EXISTS `pxc_seq_5ca53be3017fc5154e3fc9419e9a9d9b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5ca53be3017fc5154e3fc9419e9a9d9b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5d4c95661ff6afa1330a73336ee501f2`
--

DROP TABLE IF EXISTS `pxc_seq_5d4c95661ff6afa1330a73336ee501f2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5d4c95661ff6afa1330a73336ee501f2` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_5fd818921288223571400c1aee914a88`
--

DROP TABLE IF EXISTS `pxc_seq_5fd818921288223571400c1aee914a88`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_5fd818921288223571400c1aee914a88` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_610a16e2bad3c9a7176164aefa1fb341`
--

DROP TABLE IF EXISTS `pxc_seq_610a16e2bad3c9a7176164aefa1fb341`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_610a16e2bad3c9a7176164aefa1fb341` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_628ebdf6865116c001e3fe58e0852202`
--

DROP TABLE IF EXISTS `pxc_seq_628ebdf6865116c001e3fe58e0852202`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_628ebdf6865116c001e3fe58e0852202` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_632b1d90d6aae091b809d9f45031dcfb`
--

DROP TABLE IF EXISTS `pxc_seq_632b1d90d6aae091b809d9f45031dcfb`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_632b1d90d6aae091b809d9f45031dcfb` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_65a1eb60d728c72b14f14e5c272248c0`
--

DROP TABLE IF EXISTS `pxc_seq_65a1eb60d728c72b14f14e5c272248c0`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_65a1eb60d728c72b14f14e5c272248c0` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_671b525b10a8c9b8ad56e24fc8faed07`
--

DROP TABLE IF EXISTS `pxc_seq_671b525b10a8c9b8ad56e24fc8faed07`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_671b525b10a8c9b8ad56e24fc8faed07` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_6890f1913541dec506d6ddf6316f4a47`
--

DROP TABLE IF EXISTS `pxc_seq_6890f1913541dec506d6ddf6316f4a47`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_6890f1913541dec506d6ddf6316f4a47` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_693590df1ad2f735b2993cc235c0e4fa`
--

DROP TABLE IF EXISTS `pxc_seq_693590df1ad2f735b2993cc235c0e4fa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_693590df1ad2f735b2993cc235c0e4fa` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_6cb8bb958b5121398160c24888c59a23`
--

DROP TABLE IF EXISTS `pxc_seq_6cb8bb958b5121398160c24888c59a23`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_6cb8bb958b5121398160c24888c59a23` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_6ce340f3887e6692f5f44347a8788e09`
--

DROP TABLE IF EXISTS `pxc_seq_6ce340f3887e6692f5f44347a8788e09`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_6ce340f3887e6692f5f44347a8788e09` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_6ef9ddcaf5169ce2e226d3eb8f6429ca`
--

DROP TABLE IF EXISTS `pxc_seq_6ef9ddcaf5169ce2e226d3eb8f6429ca`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_6ef9ddcaf5169ce2e226d3eb8f6429ca` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_72e422f11c0809fdfe73a5d5ce23d324`
--

DROP TABLE IF EXISTS `pxc_seq_72e422f11c0809fdfe73a5d5ce23d324`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_72e422f11c0809fdfe73a5d5ce23d324` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_73b4da6201bd053f82209b746d245571`
--

DROP TABLE IF EXISTS `pxc_seq_73b4da6201bd053f82209b746d245571`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_73b4da6201bd053f82209b746d245571` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7451f6d8b8bfc9d0558b5c1292004914`
--

DROP TABLE IF EXISTS `pxc_seq_7451f6d8b8bfc9d0558b5c1292004914`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7451f6d8b8bfc9d0558b5c1292004914` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7569d23ae1749b2764e5e9908216bd95`
--

DROP TABLE IF EXISTS `pxc_seq_7569d23ae1749b2764e5e9908216bd95`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7569d23ae1749b2764e5e9908216bd95` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_75d54dfc8a5837c6d5a7784417362ba7`
--

DROP TABLE IF EXISTS `pxc_seq_75d54dfc8a5837c6d5a7784417362ba7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_75d54dfc8a5837c6d5a7784417362ba7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_77083fcc1fb8b4858a4a18fbbeb59409`
--

DROP TABLE IF EXISTS `pxc_seq_77083fcc1fb8b4858a4a18fbbeb59409`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_77083fcc1fb8b4858a4a18fbbeb59409` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_78cd16b1032b4841e92ea4607df87eeb`
--

DROP TABLE IF EXISTS `pxc_seq_78cd16b1032b4841e92ea4607df87eeb`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_78cd16b1032b4841e92ea4607df87eeb` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_78d5c1e0dd23c1ffb16aec4a0bccb0b3`
--

DROP TABLE IF EXISTS `pxc_seq_78d5c1e0dd23c1ffb16aec4a0bccb0b3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_78d5c1e0dd23c1ffb16aec4a0bccb0b3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_792f64be1dc02e3a458b47eb2279f3dc`
--

DROP TABLE IF EXISTS `pxc_seq_792f64be1dc02e3a458b47eb2279f3dc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_792f64be1dc02e3a458b47eb2279f3dc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_799541e8ac60c420141ee218c31fa9e3`
--

DROP TABLE IF EXISTS `pxc_seq_799541e8ac60c420141ee218c31fa9e3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_799541e8ac60c420141ee218c31fa9e3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7a77905341a02d13ba92f2e58cad1c93`
--

DROP TABLE IF EXISTS `pxc_seq_7a77905341a02d13ba92f2e58cad1c93`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7a77905341a02d13ba92f2e58cad1c93` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7b9a79338c0c5cbb04012485749ae711`
--

DROP TABLE IF EXISTS `pxc_seq_7b9a79338c0c5cbb04012485749ae711`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7b9a79338c0c5cbb04012485749ae711` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7cb6ccb5c136935d085881626a3990c6`
--

DROP TABLE IF EXISTS `pxc_seq_7cb6ccb5c136935d085881626a3990c6`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7cb6ccb5c136935d085881626a3990c6` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7ce5115355e263981dd48b9d1f1791aa`
--

DROP TABLE IF EXISTS `pxc_seq_7ce5115355e263981dd48b9d1f1791aa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7ce5115355e263981dd48b9d1f1791aa` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7d0a336b54beeda786146af9f044ed13`
--

DROP TABLE IF EXISTS `pxc_seq_7d0a336b54beeda786146af9f044ed13`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7d0a336b54beeda786146af9f044ed13` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7d26ae966daa6096f0cfbee47607eab7`
--

DROP TABLE IF EXISTS `pxc_seq_7d26ae966daa6096f0cfbee47607eab7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7d26ae966daa6096f0cfbee47607eab7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7dcfbda11f7f30833f95c01345a2aeeb`
--

DROP TABLE IF EXISTS `pxc_seq_7dcfbda11f7f30833f95c01345a2aeeb`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7dcfbda11f7f30833f95c01345a2aeeb` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_7e93ef95b7057a4ad71dd697325ba437`
--

DROP TABLE IF EXISTS `pxc_seq_7e93ef95b7057a4ad71dd697325ba437`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_7e93ef95b7057a4ad71dd697325ba437` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_80014c7b43760a62df86b6e3232f832f`
--

DROP TABLE IF EXISTS `pxc_seq_80014c7b43760a62df86b6e3232f832f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_80014c7b43760a62df86b6e3232f832f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_809f868357886280ef44102d59b76d22`
--

DROP TABLE IF EXISTS `pxc_seq_809f868357886280ef44102d59b76d22`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_809f868357886280ef44102d59b76d22` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_80bf46ea80c3fa81bdf5ce41776ec030`
--

DROP TABLE IF EXISTS `pxc_seq_80bf46ea80c3fa81bdf5ce41776ec030`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_80bf46ea80c3fa81bdf5ce41776ec030` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_80c623759b25db712701bbe4e674aa2a`
--

DROP TABLE IF EXISTS `pxc_seq_80c623759b25db712701bbe4e674aa2a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_80c623759b25db712701bbe4e674aa2a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_80cd03233269a5f0d4baea2c05018b22`
--

DROP TABLE IF EXISTS `pxc_seq_80cd03233269a5f0d4baea2c05018b22`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_80cd03233269a5f0d4baea2c05018b22` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8284293f7bb1fd610953c0fc3a929616`
--

DROP TABLE IF EXISTS `pxc_seq_8284293f7bb1fd610953c0fc3a929616`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8284293f7bb1fd610953c0fc3a929616` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_82b27a9c4112554353544f6bd2a222e6`
--

DROP TABLE IF EXISTS `pxc_seq_82b27a9c4112554353544f6bd2a222e6`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_82b27a9c4112554353544f6bd2a222e6` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_848542e793738a1ca52a726caffa60fc`
--

DROP TABLE IF EXISTS `pxc_seq_848542e793738a1ca52a726caffa60fc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_848542e793738a1ca52a726caffa60fc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_876054161e2ab7db263cf66abe0063d9`
--

DROP TABLE IF EXISTS `pxc_seq_876054161e2ab7db263cf66abe0063d9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_876054161e2ab7db263cf66abe0063d9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8778c308e395a02def187374044fc512`
--

DROP TABLE IF EXISTS `pxc_seq_8778c308e395a02def187374044fc512`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8778c308e395a02def187374044fc512` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_88e5a166eb1ddece1755c5d2da849f47`
--

DROP TABLE IF EXISTS `pxc_seq_88e5a166eb1ddece1755c5d2da849f47`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_88e5a166eb1ddece1755c5d2da849f47` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8914c97bd44daac0fb49cb6984550084`
--

DROP TABLE IF EXISTS `pxc_seq_8914c97bd44daac0fb49cb6984550084`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8914c97bd44daac0fb49cb6984550084` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8b176a77db793458ac44c2ae44a4d07c`
--

DROP TABLE IF EXISTS `pxc_seq_8b176a77db793458ac44c2ae44a4d07c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8b176a77db793458ac44c2ae44a4d07c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8bf31cfb7364f0537d7d341a2ff3ac5e`
--

DROP TABLE IF EXISTS `pxc_seq_8bf31cfb7364f0537d7d341a2ff3ac5e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8bf31cfb7364f0537d7d341a2ff3ac5e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8e087ccbe4958c4fed60a651d2421337`
--

DROP TABLE IF EXISTS `pxc_seq_8e087ccbe4958c4fed60a651d2421337`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8e087ccbe4958c4fed60a651d2421337` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8ebda85cf81f52cbeb73a396174a6d03`
--

DROP TABLE IF EXISTS `pxc_seq_8ebda85cf81f52cbeb73a396174a6d03`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8ebda85cf81f52cbeb73a396174a6d03` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_8ee24a97e3b4923acc4acb96ecd81927`
--

DROP TABLE IF EXISTS `pxc_seq_8ee24a97e3b4923acc4acb96ecd81927`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_8ee24a97e3b4923acc4acb96ecd81927` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_900d498bb6e6080ea279c3e452a2e064`
--

DROP TABLE IF EXISTS `pxc_seq_900d498bb6e6080ea279c3e452a2e064`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_900d498bb6e6080ea279c3e452a2e064` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_92af5ff874b1bf1c9f055e945b785e93`
--

DROP TABLE IF EXISTS `pxc_seq_92af5ff874b1bf1c9f055e945b785e93`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_92af5ff874b1bf1c9f055e945b785e93` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_93074c72fbe93989549321d9a79a42ec`
--

DROP TABLE IF EXISTS `pxc_seq_93074c72fbe93989549321d9a79a42ec`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_93074c72fbe93989549321d9a79a42ec` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_93775d4ac243a9dd306b002447a674a3`
--

DROP TABLE IF EXISTS `pxc_seq_93775d4ac243a9dd306b002447a674a3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_93775d4ac243a9dd306b002447a674a3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_937b1a5ef861a0290f95e057d8ee7d17`
--

DROP TABLE IF EXISTS `pxc_seq_937b1a5ef861a0290f95e057d8ee7d17`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_937b1a5ef861a0290f95e057d8ee7d17` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9410b6624de09c30ab4605bfc7c58608`
--

DROP TABLE IF EXISTS `pxc_seq_9410b6624de09c30ab4605bfc7c58608`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9410b6624de09c30ab4605bfc7c58608` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9447b9f7bc3fb4c3bd74f26b363c458a`
--

DROP TABLE IF EXISTS `pxc_seq_9447b9f7bc3fb4c3bd74f26b363c458a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9447b9f7bc3fb4c3bd74f26b363c458a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_95d7efecb9098e18adf2d70535c9d1a6`
--

DROP TABLE IF EXISTS `pxc_seq_95d7efecb9098e18adf2d70535c9d1a6`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_95d7efecb9098e18adf2d70535c9d1a6` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_98abc02361e0b2cb4f837bf331dc1595`
--

DROP TABLE IF EXISTS `pxc_seq_98abc02361e0b2cb4f837bf331dc1595`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_98abc02361e0b2cb4f837bf331dc1595` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_98d13db089dfe6630095ec0f6e6d9b49`
--

DROP TABLE IF EXISTS `pxc_seq_98d13db089dfe6630095ec0f6e6d9b49`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_98d13db089dfe6630095ec0f6e6d9b49` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_99161be9ef6e81eaf1182351e3ef7c3e`
--

DROP TABLE IF EXISTS `pxc_seq_99161be9ef6e81eaf1182351e3ef7c3e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_99161be9ef6e81eaf1182351e3ef7c3e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9a62ed4564a017b94913571e8a661263`
--

DROP TABLE IF EXISTS `pxc_seq_9a62ed4564a017b94913571e8a661263`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9a62ed4564a017b94913571e8a661263` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9bfe5bc21681451757c9816a514a3447`
--

DROP TABLE IF EXISTS `pxc_seq_9bfe5bc21681451757c9816a514a3447`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9bfe5bc21681451757c9816a514a3447` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9d36f16c6d016c398a8a7e28752ba024`
--

DROP TABLE IF EXISTS `pxc_seq_9d36f16c6d016c398a8a7e28752ba024`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9d36f16c6d016c398a8a7e28752ba024` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9d5e1770500ba7551c224cf318c98f73`
--

DROP TABLE IF EXISTS `pxc_seq_9d5e1770500ba7551c224cf318c98f73`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9d5e1770500ba7551c224cf318c98f73` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9d61c15d0294fbe184b23eef7ac43521`
--

DROP TABLE IF EXISTS `pxc_seq_9d61c15d0294fbe184b23eef7ac43521`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9d61c15d0294fbe184b23eef7ac43521` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9dc86d3d5ef4b9b8f70e429be0960338`
--

DROP TABLE IF EXISTS `pxc_seq_9dc86d3d5ef4b9b8f70e429be0960338`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9dc86d3d5ef4b9b8f70e429be0960338` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_9e65503be3d95e1e269f1ae8b060bc1b`
--

DROP TABLE IF EXISTS `pxc_seq_9e65503be3d95e1e269f1ae8b060bc1b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_9e65503be3d95e1e269f1ae8b060bc1b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a00f95c83703373f7a929889d8d1592f`
--

DROP TABLE IF EXISTS `pxc_seq_a00f95c83703373f7a929889d8d1592f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a00f95c83703373f7a929889d8d1592f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a084097f4caae299d5efff36db83ff30`
--

DROP TABLE IF EXISTS `pxc_seq_a084097f4caae299d5efff36db83ff30`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a084097f4caae299d5efff36db83ff30` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a0cb8363955d9e984dab82a0dcd2f60d`
--

DROP TABLE IF EXISTS `pxc_seq_a0cb8363955d9e984dab82a0dcd2f60d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a0cb8363955d9e984dab82a0dcd2f60d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a2caebb4523d8c391f0135e4667134a5`
--

DROP TABLE IF EXISTS `pxc_seq_a2caebb4523d8c391f0135e4667134a5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a2caebb4523d8c391f0135e4667134a5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a327c391ac894f365bb8f9ff671acdc2`
--

DROP TABLE IF EXISTS `pxc_seq_a327c391ac894f365bb8f9ff671acdc2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a327c391ac894f365bb8f9ff671acdc2` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a4ec317b3bd82476cc39a89a6644d1ce`
--

DROP TABLE IF EXISTS `pxc_seq_a4ec317b3bd82476cc39a89a6644d1ce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a4ec317b3bd82476cc39a89a6644d1ce` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a530b89a66ea17788dfbad15d073364f`
--

DROP TABLE IF EXISTS `pxc_seq_a530b89a66ea17788dfbad15d073364f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a530b89a66ea17788dfbad15d073364f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a7e33952bb7d5d2a64a02be5d050b147`
--

DROP TABLE IF EXISTS `pxc_seq_a7e33952bb7d5d2a64a02be5d050b147`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a7e33952bb7d5d2a64a02be5d050b147` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a970ab362d015a94747e9e5d52dae98e`
--

DROP TABLE IF EXISTS `pxc_seq_a970ab362d015a94747e9e5d52dae98e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a970ab362d015a94747e9e5d52dae98e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a98a9684ff36ae2ff342ec6c0bf18e2c`
--

DROP TABLE IF EXISTS `pxc_seq_a98a9684ff36ae2ff342ec6c0bf18e2c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a98a9684ff36ae2ff342ec6c0bf18e2c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_a9dc275d23f02ddfdb76f403f8686a1a`
--

DROP TABLE IF EXISTS `pxc_seq_a9dc275d23f02ddfdb76f403f8686a1a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_a9dc275d23f02ddfdb76f403f8686a1a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_aa253e15cf5927c2dbcae86bec71d527`
--

DROP TABLE IF EXISTS `pxc_seq_aa253e15cf5927c2dbcae86bec71d527`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_aa253e15cf5927c2dbcae86bec71d527` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_aaa92fd0139a40f149e60c3f4b4bbd74`
--

DROP TABLE IF EXISTS `pxc_seq_aaa92fd0139a40f149e60c3f4b4bbd74`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_aaa92fd0139a40f149e60c3f4b4bbd74` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_aab2550b1a6c1cf0e9a6421f535ca36a`
--

DROP TABLE IF EXISTS `pxc_seq_aab2550b1a6c1cf0e9a6421f535ca36a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_aab2550b1a6c1cf0e9a6421f535ca36a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ab7a4347af08df43b7ac4552745a376e`
--

DROP TABLE IF EXISTS `pxc_seq_ab7a4347af08df43b7ac4552745a376e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ab7a4347af08df43b7ac4552745a376e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_abbb32ca62ae63d6657d82562a32c7b4`
--

DROP TABLE IF EXISTS `pxc_seq_abbb32ca62ae63d6657d82562a32c7b4`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_abbb32ca62ae63d6657d82562a32c7b4` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_abcf9070e3696c85fa753565049a1c47`
--

DROP TABLE IF EXISTS `pxc_seq_abcf9070e3696c85fa753565049a1c47`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_abcf9070e3696c85fa753565049a1c47` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ac331ed1b1ad27c6186cc343455edc6b`
--

DROP TABLE IF EXISTS `pxc_seq_ac331ed1b1ad27c6186cc343455edc6b`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ac331ed1b1ad27c6186cc343455edc6b` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_acebf50d168f670c00d773e7f8a45f99`
--

DROP TABLE IF EXISTS `pxc_seq_acebf50d168f670c00d773e7f8a45f99`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_acebf50d168f670c00d773e7f8a45f99` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ad58b1770a19ac2b25fad52a69431bac`
--

DROP TABLE IF EXISTS `pxc_seq_ad58b1770a19ac2b25fad52a69431bac`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ad58b1770a19ac2b25fad52a69431bac` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ade043e791016500f55eb0f4cbe91ab5`
--

DROP TABLE IF EXISTS `pxc_seq_ade043e791016500f55eb0f4cbe91ab5`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ade043e791016500f55eb0f4cbe91ab5` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ae67e1277288304845f1c17c86185109`
--

DROP TABLE IF EXISTS `pxc_seq_ae67e1277288304845f1c17c86185109`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ae67e1277288304845f1c17c86185109` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_af5fdd7624a99a030cecf4528dbcd063`
--

DROP TABLE IF EXISTS `pxc_seq_af5fdd7624a99a030cecf4528dbcd063`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_af5fdd7624a99a030cecf4528dbcd063` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b063b3aaff710d0895949973c6dfb199`
--

DROP TABLE IF EXISTS `pxc_seq_b063b3aaff710d0895949973c6dfb199`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b063b3aaff710d0895949973c6dfb199` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b23aed952e35f88fb8e68d153506df65`
--

DROP TABLE IF EXISTS `pxc_seq_b23aed952e35f88fb8e68d153506df65`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b23aed952e35f88fb8e68d153506df65` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b3d567136a683e7625923f1633a100e3`
--

DROP TABLE IF EXISTS `pxc_seq_b3d567136a683e7625923f1633a100e3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b3d567136a683e7625923f1633a100e3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b44521cf9942ce730ee3b27c7fcc174a`
--

DROP TABLE IF EXISTS `pxc_seq_b44521cf9942ce730ee3b27c7fcc174a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b44521cf9942ce730ee3b27c7fcc174a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b72850ac2d986a78cc1031e7fa3428b8`
--

DROP TABLE IF EXISTS `pxc_seq_b72850ac2d986a78cc1031e7fa3428b8`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b72850ac2d986a78cc1031e7fa3428b8` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b7c60617d2a58254690256139753cfc9`
--

DROP TABLE IF EXISTS `pxc_seq_b7c60617d2a58254690256139753cfc9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b7c60617d2a58254690256139753cfc9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_b9e16219fb5d0266dcb6f08ac51b9197`
--

DROP TABLE IF EXISTS `pxc_seq_b9e16219fb5d0266dcb6f08ac51b9197`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_b9e16219fb5d0266dcb6f08ac51b9197` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_be401d1c3b3feb5ec82b39bff534d348`
--

DROP TABLE IF EXISTS `pxc_seq_be401d1c3b3feb5ec82b39bff534d348`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_be401d1c3b3feb5ec82b39bff534d348` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c05e931f4164c4a725566ae952e832fc`
--

DROP TABLE IF EXISTS `pxc_seq_c05e931f4164c4a725566ae952e832fc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c05e931f4164c4a725566ae952e832fc` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c08cbe2d1c4b6a6e6af05c004a4ff5dd`
--

DROP TABLE IF EXISTS `pxc_seq_c08cbe2d1c4b6a6e6af05c004a4ff5dd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c08cbe2d1c4b6a6e6af05c004a4ff5dd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c13996950ec390cbaa78b0205104e842`
--

DROP TABLE IF EXISTS `pxc_seq_c13996950ec390cbaa78b0205104e842`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c13996950ec390cbaa78b0205104e842` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c1ec8b7c9e57b9624403d7eaf749e2af`
--

DROP TABLE IF EXISTS `pxc_seq_c1ec8b7c9e57b9624403d7eaf749e2af`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c1ec8b7c9e57b9624403d7eaf749e2af` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c51f897c3969be08f794a3474df43503`
--

DROP TABLE IF EXISTS `pxc_seq_c51f897c3969be08f794a3474df43503`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c51f897c3969be08f794a3474df43503` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c56d862f7ab2c1afe205797157a8f026`
--

DROP TABLE IF EXISTS `pxc_seq_c56d862f7ab2c1afe205797157a8f026`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c56d862f7ab2c1afe205797157a8f026` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c5dc9cc9d1dcf38d508a74464e0ae3d3`
--

DROP TABLE IF EXISTS `pxc_seq_c5dc9cc9d1dcf38d508a74464e0ae3d3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c5dc9cc9d1dcf38d508a74464e0ae3d3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c6e6661c68798a6acd24d100567f8cce`
--

DROP TABLE IF EXISTS `pxc_seq_c6e6661c68798a6acd24d100567f8cce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c6e6661c68798a6acd24d100567f8cce` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_c8c8255f93060ca8f199d246669497d9`
--

DROP TABLE IF EXISTS `pxc_seq_c8c8255f93060ca8f199d246669497d9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_c8c8255f93060ca8f199d246669497d9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_cc301a873e31beee14546728943e2089`
--

DROP TABLE IF EXISTS `pxc_seq_cc301a873e31beee14546728943e2089`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_cc301a873e31beee14546728943e2089` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_cdd90a04dbdf536527bdb3561088d67c`
--

DROP TABLE IF EXISTS `pxc_seq_cdd90a04dbdf536527bdb3561088d67c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_cdd90a04dbdf536527bdb3561088d67c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_cdfdca33ed685dd6fd0c4ee44e7d289a`
--

DROP TABLE IF EXISTS `pxc_seq_cdfdca33ed685dd6fd0c4ee44e7d289a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_cdfdca33ed685dd6fd0c4ee44e7d289a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ce4832b3e04d7db117728885a35d5d51`
--

DROP TABLE IF EXISTS `pxc_seq_ce4832b3e04d7db117728885a35d5d51`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ce4832b3e04d7db117728885a35d5d51` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_cf3858c9b15693e999ff4b8a0a896cbf`
--

DROP TABLE IF EXISTS `pxc_seq_cf3858c9b15693e999ff4b8a0a896cbf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_cf3858c9b15693e999ff4b8a0a896cbf` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_cfc20a3f1a4df828209113f906fb5f5c`
--

DROP TABLE IF EXISTS `pxc_seq_cfc20a3f1a4df828209113f906fb5f5c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_cfc20a3f1a4df828209113f906fb5f5c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_d0b5a99cd93afc25e0312cb753bda737`
--

DROP TABLE IF EXISTS `pxc_seq_d0b5a99cd93afc25e0312cb753bda737`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_d0b5a99cd93afc25e0312cb753bda737` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_d214eb10923170cbf24531cb2cd5fd85`
--

DROP TABLE IF EXISTS `pxc_seq_d214eb10923170cbf24531cb2cd5fd85`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_d214eb10923170cbf24531cb2cd5fd85` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_d2a5f6ddea5ca75cc57877a8c6d76f2e`
--

DROP TABLE IF EXISTS `pxc_seq_d2a5f6ddea5ca75cc57877a8c6d76f2e`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_d2a5f6ddea5ca75cc57877a8c6d76f2e` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_d31cf3958b57549db300e9f2b866798a`
--

DROP TABLE IF EXISTS `pxc_seq_d31cf3958b57549db300e9f2b866798a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_d31cf3958b57549db300e9f2b866798a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_d59729abe4e23f3c4243ba50916e4993`
--

DROP TABLE IF EXISTS `pxc_seq_d59729abe4e23f3c4243ba50916e4993`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_d59729abe4e23f3c4243ba50916e4993` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dad7372f240edc9552f064ca564423c7`
--

DROP TABLE IF EXISTS `pxc_seq_dad7372f240edc9552f064ca564423c7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dad7372f240edc9552f064ca564423c7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dae3afe93347c65d8ad3bbd484a1bd93`
--

DROP TABLE IF EXISTS `pxc_seq_dae3afe93347c65d8ad3bbd484a1bd93`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dae3afe93347c65d8ad3bbd484a1bd93` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dc50337506564a63785782d9017a4330`
--

DROP TABLE IF EXISTS `pxc_seq_dc50337506564a63785782d9017a4330`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dc50337506564a63785782d9017a4330` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dc80edeccd8f5dd2d563e60399a5ed00`
--

DROP TABLE IF EXISTS `pxc_seq_dc80edeccd8f5dd2d563e60399a5ed00`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dc80edeccd8f5dd2d563e60399a5ed00` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dc930f14b176d30264682799a6a9efe4`
--

DROP TABLE IF EXISTS `pxc_seq_dc930f14b176d30264682799a6a9efe4`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dc930f14b176d30264682799a6a9efe4` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_dd95518fe262216ea754caa2ed87d353`
--

DROP TABLE IF EXISTS `pxc_seq_dd95518fe262216ea754caa2ed87d353`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_dd95518fe262216ea754caa2ed87d353` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_deaef6bc7ec0c0295f3b22f9559a4517`
--

DROP TABLE IF EXISTS `pxc_seq_deaef6bc7ec0c0295f3b22f9559a4517`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_deaef6bc7ec0c0295f3b22f9559a4517` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_df00e0d4d193b23f128b177d16c64c86`
--

DROP TABLE IF EXISTS `pxc_seq_df00e0d4d193b23f128b177d16c64c86`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_df00e0d4d193b23f128b177d16c64c86` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_df0a7fe91c19e4530d0cf89368ec2194`
--

DROP TABLE IF EXISTS `pxc_seq_df0a7fe91c19e4530d0cf89368ec2194`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_df0a7fe91c19e4530d0cf89368ec2194` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_df67197f35759aacd2d07f5a70478e83`
--

DROP TABLE IF EXISTS `pxc_seq_df67197f35759aacd2d07f5a70478e83`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_df67197f35759aacd2d07f5a70478e83` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e02acdd0a769c14f4931265be1c3a40d`
--

DROP TABLE IF EXISTS `pxc_seq_e02acdd0a769c14f4931265be1c3a40d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e02acdd0a769c14f4931265be1c3a40d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e0e72263fb344059bbe4832a913bbf4a`
--

DROP TABLE IF EXISTS `pxc_seq_e0e72263fb344059bbe4832a913bbf4a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e0e72263fb344059bbe4832a913bbf4a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e285bcef05916fed6cf2ea9cae34d52c`
--

DROP TABLE IF EXISTS `pxc_seq_e285bcef05916fed6cf2ea9cae34d52c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e285bcef05916fed6cf2ea9cae34d52c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e2cc9fe4c0410180f3ef64558e4fbc82`
--

DROP TABLE IF EXISTS `pxc_seq_e2cc9fe4c0410180f3ef64558e4fbc82`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e2cc9fe4c0410180f3ef64558e4fbc82` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e307b023a5f5615e99b5a2b720eba4a3`
--

DROP TABLE IF EXISTS `pxc_seq_e307b023a5f5615e99b5a2b720eba4a3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e307b023a5f5615e99b5a2b720eba4a3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e33c28726b0826fcf2069a2c23e7d0e9`
--

DROP TABLE IF EXISTS `pxc_seq_e33c28726b0826fcf2069a2c23e7d0e9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e33c28726b0826fcf2069a2c23e7d0e9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e410a4a4d802207f2e2903eb5507efcd`
--

DROP TABLE IF EXISTS `pxc_seq_e410a4a4d802207f2e2903eb5507efcd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e410a4a4d802207f2e2903eb5507efcd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e44598580a116673d4830bf0d10c3ffd`
--

DROP TABLE IF EXISTS `pxc_seq_e44598580a116673d4830bf0d10c3ffd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e44598580a116673d4830bf0d10c3ffd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e5996ffdee2a51dfc1e30fb8ad9ae6c6`
--

DROP TABLE IF EXISTS `pxc_seq_e5996ffdee2a51dfc1e30fb8ad9ae6c6`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e5996ffdee2a51dfc1e30fb8ad9ae6c6` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e6f9c5e8144c9140ba06300690b8bbd2`
--

DROP TABLE IF EXISTS `pxc_seq_e6f9c5e8144c9140ba06300690b8bbd2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e6f9c5e8144c9140ba06300690b8bbd2` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e7189070fd33a8dcd75211d1cf6cbb45`
--

DROP TABLE IF EXISTS `pxc_seq_e7189070fd33a8dcd75211d1cf6cbb45`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e7189070fd33a8dcd75211d1cf6cbb45` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e86aa6031aacd2a1e824e225af529969`
--

DROP TABLE IF EXISTS `pxc_seq_e86aa6031aacd2a1e824e225af529969`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e86aa6031aacd2a1e824e225af529969` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_e90e79ae578315e3bc2c5340da64445a`
--

DROP TABLE IF EXISTS `pxc_seq_e90e79ae578315e3bc2c5340da64445a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_e90e79ae578315e3bc2c5340da64445a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ea26137bdf215d8008a63ae8a286529d`
--

DROP TABLE IF EXISTS `pxc_seq_ea26137bdf215d8008a63ae8a286529d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ea26137bdf215d8008a63ae8a286529d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_eb16461857648b5f50febf90782f569f`
--

DROP TABLE IF EXISTS `pxc_seq_eb16461857648b5f50febf90782f569f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_eb16461857648b5f50febf90782f569f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_eb796b2f4a29dee41b29da6449fe0108`
--

DROP TABLE IF EXISTS `pxc_seq_eb796b2f4a29dee41b29da6449fe0108`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_eb796b2f4a29dee41b29da6449fe0108` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ec6e06977e7fa11f401f2c77c3afe19c`
--

DROP TABLE IF EXISTS `pxc_seq_ec6e06977e7fa11f401f2c77c3afe19c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ec6e06977e7fa11f401f2c77c3afe19c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ed1f77314ff1690291be1d8196fc8936`
--

DROP TABLE IF EXISTS `pxc_seq_ed1f77314ff1690291be1d8196fc8936`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ed1f77314ff1690291be1d8196fc8936` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ed9f3291adb402752f17ac424044b655`
--

DROP TABLE IF EXISTS `pxc_seq_ed9f3291adb402752f17ac424044b655`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ed9f3291adb402752f17ac424044b655` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_eeac6298a6c5f443232f8e9f102ab735`
--

DROP TABLE IF EXISTS `pxc_seq_eeac6298a6c5f443232f8e9f102ab735`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_eeac6298a6c5f443232f8e9f102ab735` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ef3f269f76acf0cea88012dcffd5f42d`
--

DROP TABLE IF EXISTS `pxc_seq_ef3f269f76acf0cea88012dcffd5f42d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ef3f269f76acf0cea88012dcffd5f42d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ef4d0b72a71afe917fddf1b88421bd0a`
--

DROP TABLE IF EXISTS `pxc_seq_ef4d0b72a71afe917fddf1b88421bd0a`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ef4d0b72a71afe917fddf1b88421bd0a` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f01c688dd9cb5082d015e8b65aa4e293`
--

DROP TABLE IF EXISTS `pxc_seq_f01c688dd9cb5082d015e8b65aa4e293`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f01c688dd9cb5082d015e8b65aa4e293` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f1b08e113bbef7a768135f710eefaf02`
--

DROP TABLE IF EXISTS `pxc_seq_f1b08e113bbef7a768135f710eefaf02`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f1b08e113bbef7a768135f710eefaf02` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f20a49209c8a799beaa651adc1665715`
--

DROP TABLE IF EXISTS `pxc_seq_f20a49209c8a799beaa651adc1665715`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f20a49209c8a799beaa651adc1665715` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f230b3db99cfab779dfe993bc2966c5c`
--

DROP TABLE IF EXISTS `pxc_seq_f230b3db99cfab779dfe993bc2966c5c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f230b3db99cfab779dfe993bc2966c5c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f257f18dcea4fd07293cfabe34806bc9`
--

DROP TABLE IF EXISTS `pxc_seq_f257f18dcea4fd07293cfabe34806bc9`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f257f18dcea4fd07293cfabe34806bc9` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f26d140b3d884e8e90d6300c4ddf293d`
--

DROP TABLE IF EXISTS `pxc_seq_f26d140b3d884e8e90d6300c4ddf293d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f26d140b3d884e8e90d6300c4ddf293d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f27f15eba08602caefd8d69add79bfba`
--

DROP TABLE IF EXISTS `pxc_seq_f27f15eba08602caefd8d69add79bfba`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f27f15eba08602caefd8d69add79bfba` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f30b89216741ce45b2c87a4a84c0ca07`
--

DROP TABLE IF EXISTS `pxc_seq_f30b89216741ce45b2c87a4a84c0ca07`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f30b89216741ce45b2c87a4a84c0ca07` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f3338f092d2b08fd7327dc380fd8b2b1`
--

DROP TABLE IF EXISTS `pxc_seq_f3338f092d2b08fd7327dc380fd8b2b1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f3338f092d2b08fd7327dc380fd8b2b1` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f3e2e23ad5dd11534468d1f71a701446`
--

DROP TABLE IF EXISTS `pxc_seq_f3e2e23ad5dd11534468d1f71a701446`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f3e2e23ad5dd11534468d1f71a701446` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f4149de0d756a35d85303f5c86f7f861`
--

DROP TABLE IF EXISTS `pxc_seq_f4149de0d756a35d85303f5c86f7f861`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f4149de0d756a35d85303f5c86f7f861` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f5000124e0f80117479c3e52de9509ed`
--

DROP TABLE IF EXISTS `pxc_seq_f5000124e0f80117479c3e52de9509ed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f5000124e0f80117479c3e52de9509ed` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f788732dbdaa03a9bbe913c617ad217c`
--

DROP TABLE IF EXISTS `pxc_seq_f788732dbdaa03a9bbe913c617ad217c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f788732dbdaa03a9bbe913c617ad217c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f7c146652832d94b9e332fc364d7c6bd`
--

DROP TABLE IF EXISTS `pxc_seq_f7c146652832d94b9e332fc364d7c6bd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f7c146652832d94b9e332fc364d7c6bd` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f7d1e886e09c7dfb0664e20a43c5623c`
--

DROP TABLE IF EXISTS `pxc_seq_f7d1e886e09c7dfb0664e20a43c5623c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f7d1e886e09c7dfb0664e20a43c5623c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_f8409d2a06fb8e21148e1a509fdd26eb`
--

DROP TABLE IF EXISTS `pxc_seq_f8409d2a06fb8e21148e1a509fdd26eb`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_f8409d2a06fb8e21148e1a509fdd26eb` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_fb6cef852624b83deea47f9d4e356f9d`
--

DROP TABLE IF EXISTS `pxc_seq_fb6cef852624b83deea47f9d4e356f9d`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_fb6cef852624b83deea47f9d4e356f9d` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_fb9702d692be5f0fc4a2dcaeb262e591`
--

DROP TABLE IF EXISTS `pxc_seq_fb9702d692be5f0fc4a2dcaeb262e591`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_fb9702d692be5f0fc4a2dcaeb262e591` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_fbc08d6a2df855fa6a13bd985133b20c`
--

DROP TABLE IF EXISTS `pxc_seq_fbc08d6a2df855fa6a13bd985133b20c`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_fbc08d6a2df855fa6a13bd985133b20c` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_fbeacdae430693400803e7b9ca837ba7`
--

DROP TABLE IF EXISTS `pxc_seq_fbeacdae430693400803e7b9ca837ba7`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_fbeacdae430693400803e7b9ca837ba7` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_fc34bffda28a1124305e015354d455b1`
--

DROP TABLE IF EXISTS `pxc_seq_fc34bffda28a1124305e015354d455b1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_fc34bffda28a1124305e015354d455b1` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_feb06d77021057eb610e655610b105f3`
--

DROP TABLE IF EXISTS `pxc_seq_feb06d77021057eb610e655610b105f3`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_feb06d77021057eb610e655610b105f3` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ff387f2f9247823ef177eca767b43707`
--

DROP TABLE IF EXISTS `pxc_seq_ff387f2f9247823ef177eca767b43707`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ff387f2f9247823ef177eca767b43707` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ff56404385813d77973b9bb161f1371f`
--

DROP TABLE IF EXISTS `pxc_seq_ff56404385813d77973b9bb161f1371f`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ff56404385813d77973b9bb161f1371f` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pxc_seq_ffc81467c73ddbcd5b547401a9f21688`
--

DROP TABLE IF EXISTS `pxc_seq_ffc81467c73ddbcd5b547401a9f21688`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `pxc_seq_ffc81467c73ddbcd5b547401a9f21688` (
  `currval` bigint(21) NOT NULL COMMENT 'current value',
  `nextval` bigint(21) NOT NULL COMMENT 'next value',
  `minvalue` bigint(21) NOT NULL COMMENT 'min value',
  `maxvalue` bigint(21) NOT NULL COMMENT 'max value',
  `start` bigint(21) NOT NULL COMMENT 'start value',
  `increment` bigint(21) NOT NULL COMMENT 'increment value',
  `cache` bigint(21) NOT NULL COMMENT 'cache size',
  `cycle` bigint(21) NOT NULL COMMENT 'cycle state',
  `round` bigint(21) NOT NULL COMMENT 'already how many round'
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quarantine_config`
--

DROP TABLE IF EXISTS `quarantine_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `quarantine_config` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(100) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `group_name` varchar(200) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `net_work_type` varchar(100) CHARACTER SET utf8 COLLATE utf8_unicode_ci DEFAULT NULL,
  `security_ip_type` varchar(100) CHARACTER SET utf8 COLLATE utf8_unicode_ci DEFAULT NULL,
  `security_ips` text CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk` (`inst_id`,`group_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `read_write_lock`
--

DROP TABLE IF EXISTS `read_write_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `read_write_lock` (
  `schema_name` varchar(64) NOT NULL,
  `owner` varchar(128) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `type` varchar(128) NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `resource` (`resource`,`type`),
  KEY `schema_name` (`schema_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recycle_bin`
--

DROP TABLE IF EXISTS `recycle_bin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `recycle_bin` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `original_name` varchar(255) NOT NULL,
  `schema_name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`,`schema_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `referential_constraints`
--

DROP TABLE IF EXISTS `referential_constraints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `referential_constraints` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `constraint_catalog` varchar(512) DEFAULT NULL,
  `constraint_schema` varchar(64) NOT NULL,
  `constraint_name` varchar(64) NOT NULL,
  `unique_constraint_catalog` varchar(512) DEFAULT NULL,
  `unique_constraint_schema` varchar(64) NOT NULL,
  `unique_constraint_name` varchar(64) DEFAULT NULL,
  `match_option` varchar(64) DEFAULT NULL,
  `update_rule` varchar(64) DEFAULT NULL,
  `delete_rule` varchar(64) DEFAULT NULL,
  `table_name` varchar(64) DEFAULT NULL,
  `referenced_table_name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `constraint_schema` (`constraint_schema`,`constraint_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_priv`
--

DROP TABLE IF EXISTS `role_priv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `role_priv` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `role_id` bigint(11) NOT NULL,
  `receiver_id` bigint(11) NOT NULL,
  `with_admin_option` tinyint(1) NOT NULL DEFAULT '0',
  `default_role` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk` (`role_id`,`receiver_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `routines`
--

DROP TABLE IF EXISTS `routines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `routines` (
  `SPECIFIC_NAME` varchar(64) NOT NULL DEFAULT '',
  `ROUTINE_CATALOG` varchar(512) NOT NULL DEFAULT '',
  `ROUTINE_SCHEMA` varchar(64) NOT NULL DEFAULT '',
  `ROUTINE_NAME` varchar(64) NOT NULL DEFAULT '',
  `ROUTINE_TYPE` varchar(9) NOT NULL DEFAULT '',
  `DATA_TYPE` varchar(64) NOT NULL DEFAULT '',
  `CHARACTER_MAXIMUM_LENGTH` int(21) DEFAULT NULL,
  `CHARACTER_OCTET_LENGTH` int(21) DEFAULT NULL,
  `NUMERIC_PRECISION` bigint(21) unsigned DEFAULT NULL,
  `NUMERIC_SCALE` int(21) DEFAULT NULL,
  `DATETIME_PRECISION` bigint(21) unsigned DEFAULT NULL,
  `CHARACTER_SET_NAME` varchar(64) DEFAULT NULL,
  `COLLATION_NAME` varchar(64) DEFAULT NULL,
  `DTD_IDENTIFIER` longtext,
  `ROUTINE_BODY` varchar(8) NOT NULL DEFAULT '',
  `ROUTINE_DEFINITION` longtext,
  `EXTERNAL_NAME` varchar(64) DEFAULT NULL,
  `EXTERNAL_LANGUAGE` varchar(64) DEFAULT NULL,
  `PARAMETER_STYLE` varchar(8) NOT NULL DEFAULT '',
  `IS_DETERMINISTIC` varchar(3) NOT NULL DEFAULT '',
  `SQL_DATA_ACCESS` varchar(64) NOT NULL DEFAULT '',
  `SQL_PATH` varchar(64) DEFAULT NULL,
  `SECURITY_TYPE` varchar(7) NOT NULL DEFAULT '',
  `CREATED` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `LAST_ALTERED` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `SQL_MODE` varchar(8192) NOT NULL DEFAULT '',
  `ROUTINE_COMMENT` longtext NOT NULL,
  `DEFINER` varchar(93) NOT NULL DEFAULT '',
  `CHARACTER_SET_CLIENT` varchar(32) NOT NULL DEFAULT '',
  `COLLATION_CONNECTION` varchar(32) NOT NULL DEFAULT '',
  `DATABASE_COLLATION` varchar(32) NOT NULL DEFAULT '',
  `ROUTINE_META` longtext NOT NULL,
  UNIQUE KEY `u_schema_name` (`ROUTINE_SCHEMA`,`ROUTINE_NAME`),
  KEY `i_name` (`ROUTINE_NAME`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_db_full_position`
--

DROP TABLE IF EXISTS `rpl_db_full_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_db_full_position` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `full_table_name` varchar(128) DEFAULT NULL,
  `total_count` bigint(20) unsigned NOT NULL DEFAULT '0',
  `finished_count` bigint(20) unsigned NOT NULL DEFAULT '0',
  `finished` int(10) unsigned NOT NULL DEFAULT '0',
  `position` varchar(256) DEFAULT NULL,
  `end_position` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_full_position` (`task_id`,`full_table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_ddl_main`
--

DROP TABLE IF EXISTS `rpl_ddl_main`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_ddl_main` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `ddl_tso` varchar(64) NOT NULL,
  `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `token` varchar(50) NOT NULL,
  `state` int(11) NOT NULL,
  `ddl_stmt` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fsm_ddl_tso` (`fsm_id`,`ddl_tso`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `service_id` (`service_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_ddl_sub`
--

DROP TABLE IF EXISTS `rpl_ddl_sub`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_ddl_sub` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `ddl_tso` varchar(64) NOT NULL,
  `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `state` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fsm_tso_task` (`fsm_id`,`ddl_tso`,`task_id`),
  KEY `service_id` (`service_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_service`
--

DROP TABLE IF EXISTS `rpl_service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_service` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `service_type` int(10) NOT NULL,
  `state_list` varchar(256) NOT NULL,
  `channel` varchar(128) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel` (`channel`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_stat_metrics`
--

DROP TABLE IF EXISTS `rpl_stat_metrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_stat_metrics` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `task_id` bigint(20) unsigned NOT NULL,
  `out_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `apply_count` bigint(20) unsigned NOT NULL DEFAULT '0',
  `in_eps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `out_bps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `in_bps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `out_insert_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `out_update_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `out_delete_rps` bigint(20) unsigned NOT NULL DEFAULT '0',
  `receive_delay` bigint(20) unsigned NOT NULL DEFAULT '0',
  `process_delay` bigint(20) unsigned NOT NULL DEFAULT '0',
  `merge_batch_size` bigint(20) unsigned NOT NULL DEFAULT '0',
  `rt` bigint(20) unsigned NOT NULL DEFAULT '0',
  `skip_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
  `skip_exception_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
  `persist_msg_counter` bigint(20) unsigned NOT NULL DEFAULT '0',
  `msg_cache_size` bigint(20) unsigned NOT NULL DEFAULT '0',
  `cpu_use_ratio` int(20) unsigned NOT NULL DEFAULT '0',
  `mem_use_ratio` int(20) unsigned NOT NULL DEFAULT '0',
  `full_gc_count` bigint(20) unsigned NOT NULL DEFAULT '0',
  `worker_ip` varchar(30) NOT NULL,
  `fsm_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task` (`task_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_state_machine`
--

DROP TABLE IF EXISTS `rpl_state_machine`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_state_machine` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `type` int(10) NOT NULL DEFAULT '0',
  `class_name` varchar(256) NOT NULL,
  `channel` varchar(128) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '0',
  `state` int(10) NOT NULL DEFAULT '0',
  `config` mediumtext COMMENT '状态机元数据',
  `cluster_id` varchar(64) DEFAULT NULL COMMENT '任务运行集群的id',
  `context` mediumtext COMMENT '状态机上下文，例如用于保存状态机运行的结果等',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel` (`channel`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_table_position`
--

DROP TABLE IF EXISTS `rpl_table_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_table_position` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `task_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `full_table_name` varchar(128) DEFAULT NULL,
  `position` varchar(256) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_position` (`task_id`,`full_table_name`),
  KEY `task_id` (`task_id`),
  KEY `service_id` (`service_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_task`
--

DROP TABLE IF EXISTS `rpl_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_task` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `gmt_heartbeat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` int(10) NOT NULL,
  `service_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `state_machine_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `type` int(10) NOT NULL DEFAULT '0',
  `master_host` varchar(256) DEFAULT NULL,
  `master_port` int(11) DEFAULT '-1',
  `extractor_config` longtext,
  `pipeline_config` longtext,
  `applier_config` longtext,
  `position` varchar(256) NOT NULL,
  `last_error` text,
  `statistic` text,
  `extra` mediumtext COMMENT '任务附加信息',
  `worker` varchar(64) DEFAULT NULL,
  `cluster_id` varchar(64) DEFAULT NULL COMMENT '任务运行集群的id',
  PRIMARY KEY (`id`),
  KEY `service_id` (`service_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpl_task_config`
--

DROP TABLE IF EXISTS `rpl_task_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rpl_task_config` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `task_id` bigint(20) unsigned NOT NULL,
  `extractor_config` longtext,
  `pipeline_config` longtext,
  `applier_config` longtext,
  `memory` int(10) NOT NULL DEFAULT '1536',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scaleout_backfill_objects`
--

DROP TABLE IF EXISTS `scaleout_backfill_objects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scaleout_backfill_objects` (
  `id` bigint(21) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL DEFAULT '',
  `table_name` varchar(64) NOT NULL DEFAULT '',
  `source_group` varchar(64) NOT NULL DEFAULT '' COMMENT 'group key',
  `target_group` varchar(64) NOT NULL DEFAULT '' COMMENT 'group key',
  `physical_table` varchar(64) NOT NULL DEFAULT '' COMMENT 'physical table name',
  `column_index` bigint(20) NOT NULL DEFAULT '-1' COMMENT 'pk column index in table',
  `parameter_method` varchar(64) NOT NULL DEFAULT '' COMMENT 'parameter method for applying last_value to extractor',
  `last_value` longtext,
  `max_value` longtext,
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:init,1:running,2:success,3:failed',
  `message` longtext,
  `success_row_count` bigint(20) unsigned NOT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `extra` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_job_db_tb_column` (`job_id`,`source_group`,`physical_table`,`column_index`),
  KEY `i_job_id` (`job_id`),
  KEY `i_job_id_status` (`job_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scaleout_checker_reports`
--

DROP TABLE IF EXISTS `scaleout_checker_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scaleout_checker_reports` (
  `id` bigint(21) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `table_schema` varchar(64) NOT NULL DEFAULT '',
  `source_physical_db` varchar(128) NOT NULL DEFAULT '' COMMENT 'group key',
  `target_physical_db` varchar(128) NOT NULL DEFAULT '' COMMENT 'group key',
  `table_name` varchar(64) NOT NULL DEFAULT '',
  `physical_table_name` varchar(64) NOT NULL DEFAULT '',
  `error_type` varchar(128) NOT NULL DEFAULT '' COMMENT 'check error type',
  `timestamp` datetime DEFAULT NULL COMMENT 'error found time',
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:found,1:repaired,2:start,3:finish',
  `primary_key` longtext,
  `details` longtext,
  `extra` longtext,
  `check_rows` bigint(20) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `i_job_id` (`job_id`),
  KEY `i_job_id_status` (`job_id`,`status`),
  KEY `i_table_name_job_id` (`table_name`,`job_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scaleout_outline`
--

DROP TABLE IF EXISTS `scaleout_outline`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scaleout_outline` (
  `id` bigint(21) unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint(20) unsigned NOT NULL,
  `job_id` bigint(20) unsigned NOT NULL,
  `parent_job_id` bigint(20) unsigned NOT NULL DEFAULT '0',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL DEFAULT '',
  `table_name` varchar(128) NOT NULL DEFAULT '',
  `source_physical_db` varchar(128) NOT NULL DEFAULT '' COMMENT 'group key',
  `target_physical_db` varchar(128) NOT NULL DEFAULT '' COMMENT 'group key',
  `old_storage_inst_id` varchar(128) DEFAULT NULL COMMENT 'old storage instance id',
  `new_storage_inst_id` varchar(128) DEFAULT NULL COMMENT 'new storage instance id',
  `src_phy_schema` varchar(128) NOT NULL DEFAULT '' COMMENT 'physical database name',
  `tar_phy_schema` varchar(128) NOT NULL DEFAULT '' COMMENT 'physical database name',
  `type` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:replicate table,1:replicate db',
  `status` bigint(10) NOT NULL DEFAULT '0' COMMENT '0:creating,1:delete_only,2:write_only,3:write_reorg,4:ready_to_public, 5:delete_reorg,6:removing,7:absent,10: doingdbmig,11: finishdbmig,12:dbreadonly,13:public',
  `job_status` varchar(64) NOT NULL DEFAULT '',
  `version` bigint(21) NOT NULL COMMENT 'table meta version',
  `remark` longtext,
  `extra` longtext,
  PRIMARY KEY (`id`),
  KEY `idx_db_tb_status` (`source_physical_db`,`table_name`,`status`),
  KEY `idx_job_id_status` (`job_id`,`job_status`),
  KEY `idx_sch_tb_status` (`table_schema`,`table_name`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scheduled_jobs`
--

DROP TABLE IF EXISTS `scheduled_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scheduled_jobs` (
  `schedule_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) DEFAULT NULL,
  `schedule_name` varchar(255) NOT NULL,
  `schedule_comment` varchar(512) DEFAULT NULL,
  `executor_type` varchar(64) NOT NULL,
  `schedule_context` longtext,
  `executor_contents` longtext,
  `status` varchar(64) NOT NULL COMMENT 'ENABLED/DISABLED',
  `schedule_type` varchar(64) NOT NULL COMMENT 'INTERVAL/CRON',
  `schedule_expr` varchar(256) DEFAULT NULL,
  `time_zone` varchar(64) NOT NULL,
  `last_fire_time` bigint(20) NOT NULL DEFAULT '0',
  `next_fire_time` bigint(20) NOT NULL DEFAULT '0',
  `starts` bigint(20) NOT NULL DEFAULT '0',
  `ends` bigint(20) NOT NULL DEFAULT '0',
  `schedule_policy` varchar(256) DEFAULT NULL COMMENT 'fire/wait/skip',
  `table_group_name` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`schedule_id`),
  UNIQUE KEY `table_schema` (`table_schema`,`schedule_name`),
  KEY `table_schema_2` (`table_schema`,`table_name`),
  KEY `next_fire_time` (`next_fire_time`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_change`
--

DROP TABLE IF EXISTS `schema_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `schema_change` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `table_name` varchar(64) NOT NULL,
  `version` int(10) unsigned NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schemata`
--

DROP TABLE IF EXISTS `schemata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `schemata` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `catalog_name` varchar(512) DEFAULT 'def',
  `schema_name` varchar(64) NOT NULL,
  `default_character_set_name` varchar(32) NOT NULL,
  `default_collation_name` varchar(32) NOT NULL,
  `sql_path` varchar(512) DEFAULT NULL,
  `default_db_index` varchar(256) NOT NULL,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sequence`
--

DROP TABLE IF EXISTS `sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sequence` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `new_name` varchar(128) DEFAULT NULL,
  `value` bigint(20) NOT NULL,
  `unit_count` int(11) NOT NULL DEFAULT '1',
  `unit_index` int(11) NOT NULL DEFAULT '0',
  `inner_step` int(11) NOT NULL DEFAULT '100000',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sequence_opt`
--

DROP TABLE IF EXISTS `sequence_opt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sequence_opt` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schema_name` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `new_name` varchar(128) DEFAULT NULL,
  `value` bigint(20) unsigned NOT NULL,
  `increment_by` int(10) unsigned NOT NULL DEFAULT '1',
  `start_with` bigint(20) unsigned NOT NULL DEFAULT '1',
  `max_value` bigint(20) unsigned NOT NULL DEFAULT '18446744073709551615',
  `cycle` tinyint(5) unsigned NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `server_info`
--

DROP TABLE IF EXISTS `server_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `server_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `inst_type` int(11) NOT NULL,
  `ip` varchar(128) NOT NULL,
  `port` int(11) NOT NULL,
  `htap_port` int(11) NOT NULL,
  `mgr_port` int(11) NOT NULL,
  `mpp_port` int(11) NOT NULL,
  `status` int(11) NOT NULL,
  `region_id` varchar(128) DEFAULT NULL,
  `azone_id` varchar(128) DEFAULT NULL,
  `idc_id` varchar(128) DEFAULT NULL,
  `cpu_core` int(11) DEFAULT NULL,
  `mem_size` int(11) DEFAULT NULL,
  `extras` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_id_addr` (`inst_id`,`ip`,`port`),
  KEY `idx_inst_id_status` (`inst_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `session_variables`
--

DROP TABLE IF EXISTS `session_variables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `session_variables` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `variable_name` varchar(64) NOT NULL,
  `variable_value` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_info`
--

DROP TABLE IF EXISTS `storage_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `storage_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `storage_inst_id` varchar(128) NOT NULL,
  `storage_master_inst_id` varchar(128) NOT NULL,
  `ip` varchar(128) NOT NULL,
  `port` int(11) NOT NULL COMMENT 'port for mysql',
  `xport` int(11) DEFAULT NULL COMMENT 'port for x-protocol',
  `user` varchar(128) NOT NULL,
  `passwd_enc` text NOT NULL,
  `storage_type` int(11) NOT NULL COMMENT '0:x-cluster, 1:mysql, 2:polardb',
  `inst_kind` int(11) NOT NULL COMMENT '0:master, 1:slave, 2:metadb',
  `status` int(11) NOT NULL COMMENT '0:storage ready, 1:storage not_ready',
  `region_id` varchar(128) DEFAULT NULL,
  `azone_id` varchar(128) DEFAULT NULL,
  `idc_id` varchar(128) DEFAULT NULL,
  `max_conn` int(11) NOT NULL,
  `cpu_core` int(11) DEFAULT NULL,
  `mem_size` int(11) DEFAULT NULL COMMENT 'mem unit: MB',
  `is_vip` int(11) DEFAULT NULL COMMENT '0:ip is NOT vip, 1:ip is vip',
  `extras` text COMMENT 'reserve for extra info',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_id_addr` (`storage_inst_id`,`ip`,`port`,`inst_kind`),
  KEY `idx_inst_id_status` (`inst_id`,`status`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_constraints`
--

DROP TABLE IF EXISTS `table_constraints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_constraints` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `constraint_catalog` varchar(512) DEFAULT NULL,
  `constraint_schema` varchar(64) NOT NULL,
  `constraint_name` varchar(64) NOT NULL,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `constraint_type` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `constraint_schema` (`constraint_schema`,`constraint_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_group`
--

DROP TABLE IF EXISTS `table_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `schema_name` varchar(64) NOT NULL,
  `tg_name` varchar(64) NOT NULL,
  `locality` text,
  `primary_zone` text,
  `inited` int(11) NOT NULL DEFAULT '0',
  `meta_version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `manual_create` int(11) NOT NULL DEFAULT '0' COMMENT '0:create implicitly, 1:create manually',
  `tg_type` int(11) NOT NULL DEFAULT '0' COMMENT '0:part_tg_group,1:default single_tg, 2:non default single_tg',
  `auto_split_policy` int(11) NOT NULL DEFAULT '0' COMMENT '0:NONE, 1:AUTO_SPLIT, 2:AUTO_SPLIT_HOT_VALUE',
  `partition_definition` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uidx_tgname` (`schema_name`,`tg_name`,`meta_version`),
  KEY `db_tg` (`schema_name`,`tg_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_local_partitions`
--

DROP TABLE IF EXISTS `table_local_partitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_local_partitions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(256) NOT NULL,
  `start_with_date` varchar(256) DEFAULT NULL,
  `interval_count` int(11) NOT NULL,
  `interval_unit` char(36) NOT NULL,
  `expire_after_count` int(11) NOT NULL COMMENT '-1:wont expire',
  `pre_allocate_count` int(11) NOT NULL,
  `pivot_date_expr` varchar(512) NOT NULL,
  `archive_table_name` varchar(255) DEFAULT NULL,
  `archive_table_schema` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheme_table` (`table_schema`,`table_name`),
  KEY `archive_scheme_table_index` (`archive_table_schema`,`archive_table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_partitions`
--

DROP TABLE IF EXISTS `table_partitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_partitions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_id` bigint(20) NOT NULL COMMENT '>0:has parent;  <=0:has no parent',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `sp_temp_flag` int(11) NOT NULL COMMENT '1:sub part is defined by template,0:defined by non-template',
  `group_id` bigint(20) NOT NULL DEFAULT '-1' COMMENT 'part_level = 0 ref to tableGroupId, next_level = -1 ref to partition group',
  `meta_version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `auto_flag` int(11) DEFAULT '0' COMMENT '0:auto rebalancing is not open, 1: auto rebalancing is open',
  `tbl_type` int(11) DEFAULT '0' COMMENT '0:primary table, 1:gsi table',
  `part_name` varchar(64) NOT NULL DEFAULT '' COMMENT 'the name of part',
  `part_temp_name` varchar(64) DEFAULT NULL COMMENT 'part name in template',
  `part_level` int(11) NOT NULL COMMENT '-1:no_next_level,0:logical,1:1st_level_part,2:2nd_level_part,3:3rd_level_part',
  `next_level` int(11) NOT NULL COMMENT '-1:no_next_level,0:logical,1:1st_level_part,2:2nd_level_part,3:3rd_level_part',
  `part_status` int(11) NOT NULL,
  `part_position` bigint(20) DEFAULT NULL,
  `part_method` varchar(64) DEFAULT NULL,
  `part_expr` longtext,
  `part_desc` longtext,
  `part_comment` longtext,
  `part_engine` varchar(64) DEFAULT NULL,
  `part_extras` longtext,
  `part_flags` bigint(20) unsigned DEFAULT '0',
  `phy_table` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pname` (`table_schema`,`table_name`,`part_name`),
  KEY `tb_level` (`table_schema`,`table_name`,`part_level`),
  KEY `grp_id` (`group_id`),
  KEY `par_id` (`parent_id`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_partitions_delta`
--

DROP TABLE IF EXISTS `table_partitions_delta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_partitions_delta` (
  `id` bigint(20) NOT NULL DEFAULT '0',
  `parent_id` bigint(20) NOT NULL COMMENT '>0:has parent; <=0:has no parent',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `sp_temp_flag` int(11) NOT NULL COMMENT '1:sub part is defined by template,0:defined by non-template',
  `group_id` bigint(20) NOT NULL DEFAULT '-1' COMMENT 'part_level = 0 ref to tableGroupId, next_level = -1 ref to partition group',
  `meta_version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `auto_flag` int(11) DEFAULT '0' COMMENT '0:auto rebalancing is not open, 1: auto rebalancing is open',
  `tbl_type` int(11) DEFAULT '0' COMMENT '0:primary table, 1:gsi table',
  `part_name` varchar(64) NOT NULL DEFAULT '' COMMENT 'the name of part',
  `part_temp_name` varchar(64) DEFAULT NULL COMMENT 'part name in template',
  `part_level` int(11) NOT NULL COMMENT '-1:no_next_level,0:logical,1:1st_level_part,2:2nd_level_part,3:3rd_level_part',
  `next_level` int(11) NOT NULL COMMENT '-1:no_next_level,0:logical,1:1st_level_part,2:2nd_level_part,3:3rd_level_part',
  `part_status` int(11) NOT NULL,
  `part_position` bigint(20) DEFAULT NULL,
  `part_method` varchar(64) DEFAULT NULL,
  `part_expr` longtext,
  `part_desc` longtext,
  `part_comment` longtext,
  `part_engine` varchar(64) DEFAULT NULL,
  `part_extras` longtext,
  `part_flags` bigint(20) unsigned DEFAULT '0',
  `phy_table` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`table_schema`,`table_name`,`part_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_priv`
--

DROP TABLE IF EXISTS `table_priv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_priv` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_name` char(32) NOT NULL DEFAULT '',
  `host` char(60) NOT NULL DEFAULT '',
  `db_name` char(64) NOT NULL DEFAULT '',
  `table_name` char(64) NOT NULL DEFAULT '',
  `select_priv` tinyint(1) NOT NULL DEFAULT '0',
  `insert_priv` tinyint(1) NOT NULL DEFAULT '0',
  `update_priv` tinyint(1) NOT NULL DEFAULT '0',
  `delete_priv` tinyint(1) NOT NULL DEFAULT '0',
  `create_priv` tinyint(1) NOT NULL DEFAULT '0',
  `drop_priv` tinyint(1) NOT NULL DEFAULT '0',
  `grant_priv` tinyint(1) NOT NULL DEFAULT '0',
  `index_priv` tinyint(1) NOT NULL DEFAULT '0',
  `alter_priv` tinyint(1) NOT NULL DEFAULT '0',
  `show_view_priv` int(11) NOT NULL DEFAULT '0',
  `create_view_priv` int(11) NOT NULL DEFAULT '0',
  `replication_client_priv` tinyint(1) NOT NULL DEFAULT '0',
  `replication_slave_priv` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk` (`user_name`,`host`,`db_name`,`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `table_statistics`
--

DROP TABLE IF EXISTS `table_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `table_statistics` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `schema_name` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `row_count` bigint(20) NOT NULL,
  `extend_field` longtext COMMENT 'Json string extend field',
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`table_name`),
  KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tables`
--

DROP TABLE IF EXISTS `tables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tables` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `new_table_name` varchar(64) DEFAULT NULL,
  `table_type` varchar(64) DEFAULT 'BASE TABLE',
  `engine` varchar(64) DEFAULT 'InnoDB',
  `version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `row_format` varchar(10) DEFAULT 'Dynamic',
  `table_rows` bigint(20) unsigned DEFAULT '0',
  `avg_row_length` bigint(20) unsigned DEFAULT '0',
  `data_length` bigint(20) unsigned DEFAULT '16384',
  `max_data_length` bigint(20) unsigned DEFAULT '0',
  `index_length` bigint(20) unsigned DEFAULT '16384',
  `data_free` bigint(20) unsigned DEFAULT '0',
  `auto_increment` bigint(20) unsigned DEFAULT '1',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `check_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `table_collation` varchar(32) DEFAULT NULL,
  `checksum` bigint(20) unsigned DEFAULT NULL,
  `create_options` varchar(255) DEFAULT NULL,
  `table_comment` varchar(2048) DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `flag` bigint(20) unsigned DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_schema` (`table_schema`,`table_name`),
  KEY `table_name` (`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tables_ext`
--

DROP TABLE IF EXISTS `tables_ext`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tables_ext` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `table_catalog` varchar(512) DEFAULT 'def',
  `table_schema` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `new_table_name` varchar(64) DEFAULT NULL,
  `table_type` int(11) NOT NULL DEFAULT '0' COMMENT '0:SINGLE,1:SHARDING,2:BROADCAST,3:GSI',
  `db_partition_key` varchar(512) DEFAULT NULL,
  `db_partition_policy` varchar(64) DEFAULT NULL,
  `db_partition_count` int(10) unsigned NOT NULL DEFAULT '1',
  `db_name_pattern` varchar(256) NOT NULL,
  `db_rule` varchar(512) DEFAULT NULL,
  `db_meta_map` mediumtext,
  `tb_partition_key` varchar(512) DEFAULT NULL,
  `tb_partition_policy` varchar(64) DEFAULT NULL,
  `tb_partition_count` int(10) unsigned NOT NULL DEFAULT '1',
  `tb_name_pattern` varchar(256) NOT NULL,
  `tb_rule` varchar(512) DEFAULT NULL,
  `tb_meta_map` mediumtext,
  `ext_partitions` mediumtext,
  `full_table_scan` int(10) unsigned NOT NULL DEFAULT '0',
  `broadcast` int(10) unsigned NOT NULL DEFAULT '0',
  `version` bigint(20) unsigned NOT NULL DEFAULT '1',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '0:ABSENT,1:PUBLIC',
  `flag` bigint(20) unsigned DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_schema` (`table_schema`,`table_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_login_error_limit`
--

DROP TABLE IF EXISTS `user_login_error_limit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_login_error_limit` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `limit_key` varchar(128) DEFAULT NULL,
  `max_error_limit` int(11) NOT NULL,
  `error_count` int(11) NOT NULL,
  `expire_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_key` (`limit_key`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_priv`
--

DROP TABLE IF EXISTS `user_priv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_priv` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_name` char(32) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `host` char(60) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `password` char(100) COLLATE utf8_unicode_ci NOT NULL,
  `select_priv` tinyint(1) NOT NULL DEFAULT '0',
  `insert_priv` tinyint(1) NOT NULL DEFAULT '0',
  `update_priv` tinyint(1) NOT NULL DEFAULT '0',
  `delete_priv` tinyint(1) NOT NULL DEFAULT '0',
  `create_priv` tinyint(1) NOT NULL DEFAULT '0',
  `drop_priv` tinyint(1) NOT NULL DEFAULT '0',
  `grant_priv` tinyint(1) NOT NULL DEFAULT '0',
  `index_priv` tinyint(1) NOT NULL DEFAULT '0',
  `alter_priv` tinyint(1) NOT NULL DEFAULT '0',
  `show_view_priv` int(11) NOT NULL DEFAULT '0',
  `create_view_priv` int(11) NOT NULL DEFAULT '0',
  `create_user_priv` int(11) NOT NULL DEFAULT '0',
  `meta_db_priv` int(11) NOT NULL DEFAULT '0',
  `account_type` tinyint(1) NOT NULL DEFAULT '0',
  `show_audit_log_priv` int(11) NOT NULL DEFAULT '0',
  `replication_client_priv` tinyint(1) NOT NULL DEFAULT '0',
  `replication_slave_priv` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk` (`user_name`,`host`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `validation_diff`
--

DROP TABLE IF EXISTS `validation_diff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `validation_diff` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `state_machine_id` varchar(64) NOT NULL,
  `service_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `validation_task_id` varchar(64) NOT NULL COMMENT '校验任务 external id',
  `type` int(11) NOT NULL COMMENT '全量校验, 增量校验',
  `state` int(11) NOT NULL COMMENT '新创建, 复检等等',
  `diff` text COMMENT 'diff detail json',
  `src_logical_db` varchar(128) DEFAULT NULL COMMENT '源端逻辑库名',
  `src_logical_table` varchar(128) DEFAULT NULL COMMENT '源端逻辑表名',
  `src_logical_key_col` varchar(128) DEFAULT NULL,
  `src_phy_db` varchar(128) DEFAULT NULL COMMENT '源端物理库名',
  `src_phy_table` varchar(128) DEFAULT NULL COMMENT '源端物理表名',
  `src_phy_key_col` varchar(128) DEFAULT NULL COMMENT '源端 pk/uk',
  `src_key_col_val` varchar(256) NOT NULL COMMENT '源端 pk/uk 值',
  `dst_logical_db` varchar(128) DEFAULT NULL COMMENT '目标端逻辑库名',
  `dst_logical_table` varchar(128) DEFAULT NULL COMMENT '目标端表名',
  `dst_logical_key_col` varchar(128) DEFAULT NULL COMMENT '目标端逻辑 pk/uk',
  `dst_key_col_val` varchar(256) NOT NULL COMMENT '目标端逻辑 pk/uk 值',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `validation_diff_srcdb_index` (`state_machine_id`,`src_phy_db`,`src_phy_table`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `validation_task`
--

DROP TABLE IF EXISTS `validation_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `validation_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `external_id` varchar(64) NOT NULL,
  `state_machine_id` varchar(64) NOT NULL COMMENT '评估迁移任务 id',
  `service_id` varchar(64) NOT NULL COMMENT '评估迁移任务各逻辑步骤 id, 如迁移任务对应 1 个全量同步任务, 1 个增量同步任务等等',
  `task_id` varchar(64) NOT NULL COMMENT '分配至当前容器的任务步骤 id. 物理概念',
  `type` int(11) NOT NULL COMMENT '校验任务类型. 1 : 全量校验, 2 : 增量校验',
  `state` int(11) NOT NULL COMMENT '当前任务运行状态 ',
  `drds_ins_id` varchar(64) DEFAULT NULL COMMENT '1.0 DRDS 实例 id',
  `rds_ins_id` varchar(64) DEFAULT NULL COMMENT '1.0 RDS 实例 id',
  `src_logical_db` varchar(64) DEFAULT NULL COMMENT '逻辑库名',
  `src_logical_table` varchar(128) DEFAULT NULL COMMENT '逻辑表名',
  `src_logical_key_col` varchar(128) DEFAULT NULL COMMENT 'pk/uk',
  `src_phy_db` varchar(64) DEFAULT NULL COMMENT '物理库名',
  `src_phy_table` varchar(128) DEFAULT NULL COMMENT '物理表名',
  `src_phy_key_col` varchar(128) DEFAULT NULL COMMENT 'physical pk/uk',
  `polardbx_ins_id` varchar(64) DEFAULT NULL COMMENT '2.0 实例 id',
  `dst_logical_db` varchar(64) DEFAULT NULL COMMENT '2.0 逻辑库名',
  `dst_logical_table` varchar(128) DEFAULT NULL COMMENT '2.0 逻辑表名',
  `dst_logical_key_col` varchar(128) DEFAULT NULL COMMENT 'target pk/uk',
  `config` text COMMENT '任务配置 json',
  `stats` text COMMENT '物理表任务统计信息. 如已校验行数, 失败行数等等',
  `task_range` varchar(1024) DEFAULT NULL COMMENT '全量校验范围',
  `deleted` tinyint(1) DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `validation_task_src_index` (`state_machine_id`,`src_phy_db`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `variable_config`
--

DROP TABLE IF EXISTS `variable_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `variable_config` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inst_id` varchar(128) NOT NULL,
  `param_key` varchar(128) NOT NULL,
  `param_val` varchar(1024) NOT NULL,
  `extra` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_key` (`param_key`)
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `views`
--

DROP TABLE IF EXISTS `views`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `views` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `catalog_name` varchar(512) DEFAULT 'def',
  `schema_name` varchar(64) NOT NULL,
  `view_name` varchar(64) NOT NULL,
  `column_list` mediumtext,
  `view_definition` longtext NOT NULL,
  `plan` longtext,
  `plan_type` varchar(255) DEFAULT NULL,
  `plan_error` longtext,
  `materialization` varchar(255) DEFAULT NULL,
  `check_option` varchar(8) DEFAULT NULL,
  `is_updatable` varchar(3) DEFAULT NULL,
  `definer` varchar(93) DEFAULT NULL,
  `security_type` varchar(7) DEFAULT NULL,
  `character_set_client` varchar(32) DEFAULT NULL,
  `collation_connection` varchar(32) DEFAULT NULL,
  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `extend_field` longtext COMMENT 'Json string extend field',
  PRIMARY KEY (`id`),
  UNIQUE KEY `schema_name` (`schema_name`,`view_name`)
);
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-08-14 11:58:16
