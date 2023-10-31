Replica Reference Manual
========================

## CHANGE MASTER TO

CHANGE MASTER TO option  [, option] ... [channel_option]

option :  {  
&emsp;MASTER_HOST='host_name'  
&emsp;| MASTER_USER='user_name'  
&emsp;| MASTER_PASSWORD='password'  
&emsp;| MASTER_PORT=port_num  
&emsp;| MASTER_HEARTBEAT_PERIOD=interval  
&emsp;| MASTER_LOG_FILE='source_log_name'  
&emsp;| MASTER_LOG_POS=source_log_pos  
&emsp;| IGNORE_SERVER_IDS=(server_id_list)  
&emsp;| SOURCE_HOST_TYPE='rds' / 'polardbx' / 'mysql'  
}

channel_option:  
&emsp;FOR CHANNEL channel

server_id_list:  
&emsp;[server_id [, server_id]...]

options supported soon:  
&emsp;MASTER_HEARTBEAT_PERIOD=interval (interval is now fixed to 120)  
&emsp;WRITER_TYPE='transaction' / 'serial' / 'split' / 'merge'
<br/>

## CHANGE REPLICATION FILTER

CHANGE REPLICATION FILTER filter [, filter][, ...]  
filter :  {  
&emsp;REPLICATE_DO_DB=(db_list)  
&emsp;| REPLICATE_IGNORE_DB=(db_list)  
&emsp;| REPLICATE_DO_TABLE=(tbl_list)  
&emsp;| REPLICATE_IGNORE_TABLE=(tbl_list)  
&emsp;| REPLICATE_WILD_DO_TABLE=(wild_tbl_list)  
&emsp;| REPLICATE_WILD_IGNORE_TABLE=(wild_tbl_list)  
&emsp;| REPLICATE_REWRITE_DB=(db_pair_list)  
}

db_list :  
&emsp;db_name[, db_name][, ... ]

tbl_list :  
&emsp;db_name.table_name[, db_table_name][, ... ]

wild_tbl_list :  
&emsp;'db_pattern.table_pattern'[, 'db_pattern.table_pattern'][, ...]

db_pair_list :  
&emsp;(db_pair)[, (db_pair)][, ...]

db_pair:  
&emsp;from_db, to_db
<br/>

## START SLAVE

START SLAVE [channel_option]

channel_option:  
&emsp;FOR CHANNEL channel
<br/>

## STOP SLAVE

STOP SLAVE [channel_option]

channel_option:  
&emsp;FOR CHANNEL channel
<br/>

## RESET SLAVE

RESET SLAVE [ALL] [channel_option]

channel_option:  
&emsp;FOR CHANNEL channel
<br/>

## Commands supported soon:

SET SQL_SLAVE_SKIP_COUNTER=N
<br/>

## More details

For more details about commands and options, refer to
MySQL [official Mysql document](https://dev.mysql.com/doc/refman/5.7/en/replication-statements-replica.html).
