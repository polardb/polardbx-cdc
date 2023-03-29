# PolarDB-X Binlog 相关命令介绍

PolarDB-X 完全兼容 MySQL Binlog 相关指令，例如 `SHOW BINARY LOGS`，`SHOW BINLOG EVENTS` 等，并在此基础上进行了一定的扩展。

下面是对 PolarDB-X 所支持的所有Binlog相关命令的详细介绍。

## SHOW MASTER STATUS

### 命令介绍

参考 [MySQL SHOW MASTER STATUS](https://dev.mysql.com/doc/refman/8.0/en/show-master-status.html)，用于获取当前
PolarDB-X 的Binlog文件信息。

### 命令使用

执行下面的命令，即可获取当前全局Binlog的文件相关信息。

```sql
SHOW
MASTER STATUS;
```

```text
mysql> show master status;
+---------------+----------+--------------+------------------+-------------------+
| FILE          | POSITION | BINLOG_DO_DB | BINLOG_IGNORE_DB | EXECUTED_GTID_SET |
+---------------+----------+--------------+------------------+-------------------+
| binlog.000014 |  8273883 |              |                  |                   |
+---------------+----------+--------------+------------------+-------------------+
1 row in set (0.07 sec)
```

执行下面的命令，即可获得当前多流Binlog中某个流的相关信息，其中 `stream_id`
可以通过下面的 `SHOW BINARY STREAMS` 命令获取。

* WITH `group name`\_stream_`stream_id`

```sql
SHOW
MASTER STATUS WITH group1_stream_0;
```

```text
mysql> show master status with group1_stream_0;
+-------------------------------+----------+--------------+------------------+-------------------+
| FILE                          | POSITION | BINLOG_DO_DB | BINLOG_IGNORE_DB | EXECUTED_GTID_SET |
+-------------------------------+----------+--------------+------------------+-------------------+
| group1_stream_0_binlog.000005 |  9256201 |              |                  |                   |
+-------------------------------+----------+--------------+------------------+-------------------+
1 row in set (0.02 sec)
```

## SHOW BINARY LOGS

### 命令介绍

参考 [MySQL SHOW BINARY LOGS](https://dev.mysql.com/doc/refman/8.0/en/show-binary-logs.html)，用于获取当前 PolarDB-X CDC
所有Binlog文件的列表。

### 命令使用

执行下面的命令，即可获取当前全局Binlog中所有Binlog文件的列表。

```sql
SHOW
BINARY LOGS;
```

```text
mysql> show binary logs;
+---------------+-----------+
| LOG_NAME      | FILE_SIZE |
+---------------+-----------+
| binlog.000001 |  10486210 |
| binlog.000002 |  10486073 |
| binlog.000003 |  10485875 |
+---------------+-----------+
3 rows in set (0.08 sec)
```

执行下面的命令，即可获得当前多流Binlog中某个流的所有Binlog文件的列表。

* WITH `group name`\_stream_`stream_id`

```sql
SHOW
BINARY LOGS WITH group1_stream_0;
```

```text
mysql> show binary logs with group1_stream_0;
+-------------------------------+-----------+
| LOG_NAME                      | FILE_SIZE |
+-------------------------------+-----------+
| group1_stream_0_binlog.000001 |  10486111 |
| group1_stream_0_binlog.000002 |  10486034 |
| group1_stream_0_binlog.000003 |  10486117 |
| group1_stream_0_binlog.000004 |  25360807 |
+-------------------------------+-----------+
4 rows in set (0.09 sec)
```

## SHOW BINLOG EVENTS

### 命令介绍

参考 [MySQL SHOW BINLOG EVENTS](https://dev.mysql.com/doc/refman/8.0/en/show-binlog-events.html)
，用于获取某个Binlog文件的指定范围内的Binlog Events。

### 命令使用

使用下面的命令，即可获取全局Binlog中某个Binlog文件的指定范围内的Binlog Events。

```sql
SHOW
BINLOG EVENTS IN 'binlog.000001' FROM 4 LIMIT 10;
```

```text
mysql> show binlog events in 'binlog.000001' limit 10;
+---------------+------+-------------+------------+-------------+-------------------------------------------------------------+
| LOG_NAME      | POS  | EVENT_TYPE  | SERVER_ID  | END_LOG_POS | INFO                                                        |
+---------------+------+-------------+------------+-------------+-------------------------------------------------------------+
| binlog.000001 |    4 | Format_desc | 1979992319 |         123 | Server ver: 5.6.29-TDDL-5.4.16-SNAPSHOT, Binlog ver: 4      |
| binlog.000001 |  123 | Rows_query  | 1979992319 |         206 | CTS::704484124684556704015759064623858360330000000000000000 |
| binlog.000001 |  206 | Rows_query  | 1979992319 |         289 | CTS::704484124774314809615759064632834170880000000000000000 |
| binlog.000001 |  289 | Rows_query  | 1979992319 |         372 | CTS::704484124864911776015759064641851924490000000000000000 |
| binlog.000001 |  372 | Rows_query  | 1979992319 |         455 | CTS::704484124955508742415759064650869678090000000000000000 |
| binlog.000001 |  455 | Rows_query  | 1979992319 |         538 | CTS::704484125045266848015759064659887431680000000000000000 |
| binlog.000001 |  538 | Rows_query  | 1979992319 |         621 | CTS::704484137280471046415759065883449794570000000000000000 |
| binlog.000001 |  621 | Rows_query  | 1979992319 |         704 | CTS::704484137370648582415759065892425605120000000000000000 |
| binlog.000001 |  704 | Rows_query  | 1979992319 |         787 | CTS::704484137460406688015759065901443358720000000000000000 |
| binlog.000001 |  787 | Rows_query  | 1979992319 |         870 | CTS::704484137550584224015759065910419169280000000000000000 |
+---------------+------+-------------+------------+-------------+-------------------------------------------------------------+
10 rows in set (3.37 sec)
```

使用下面的命令，即可获得多流Binlog中某个流中某个Binlog文件的指定范围内的Binlog Events。

* WITH `group name`\_stream_`stream_id`
* IN `binlog file name`
* [ FROM `start position` ]
* [ LIMIT `number of events` ]

```sql
SHOW
BINLOG EVENTS WITH 'group1_stream_0' IN 'group1_stream_0_binlog.000001' LIMIT 10;
```

```text
mysql> show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' limit 10;
+-------------------------------+------+-------------+------------+-------------+-------------------------------------------------------------+
| LOG_NAME                      | POS  | EVENT_TYPE  | SERVER_ID  | END_LOG_POS | INFO                                                        |
+-------------------------------+------+-------------+------------+-------------+-------------------------------------------------------------+
| group1_stream_0_binlog.000001 |    4 | Format_desc | 1979992319 |         123 | Server ver: 5.6.29-TDDL-5.4.16-SNAPSHOT, Binlog ver: 4      |
| group1_stream_0_binlog.000001 |  123 | Rows_query  | 1979992319 |         206 | CTS::704484112107097299215759063365944647680000000000000000 |
| group1_stream_0_binlog.000001 |  206 | Rows_query  | 1979992319 |         289 | CTS::704484112199791417615759063375172116480000000000000000 |
| group1_stream_0_binlog.000001 |  289 | Rows_query  | 1979992319 |         372 | CTS::704484112292066105615759063384441528320000000000000000 |
| group1_stream_0_binlog.000001 |  372 | Rows_query  | 1979992319 |         455 | CTS::704484112384760224015759063393668997120000000000000000 |
| group1_stream_0_binlog.000001 |  455 | Rows_query  | 1979992319 |         538 | CTS::704484112476615481615759063402896465920000000000000000 |
| group1_stream_0_binlog.000001 |  538 | Rows_query  | 1979992319 |         621 | CTS::704484124684556704015759064623858360330000000000000000 |
| group1_stream_0_binlog.000001 |  621 | Rows_query  | 1979992319 |         704 | CTS::704484124774314809615759064632834170880000000000000000 |
| group1_stream_0_binlog.000001 |  704 | Rows_query  | 1979992319 |         787 | CTS::704484124864911776015759064641851924490000000000000000 |
| group1_stream_0_binlog.000001 |  787 | Rows_query  | 1979992319 |         870 | CTS::704484124955508742415759064650869678090000000000000000 |
+-------------------------------+------+-------------+------------+-------------+-------------------------------------------------------------+
10 rows in set (0.75 sec)
```

## SHOW BINARY STREAMS

### 命令介绍

获取多流Binlog中所有流的相关信息，包括组名、流名、当前流的Binlog文件名、当前流的Binlog文件最新位点等。

### 命令使用

执行下面的命令，即可获取多流Binlog中所有流的相关信息。

```sql
SHOW
BINARY STREAMS;
```

```text
mysql> show binary streams;
+--------+-----------------+-------------------------------+----------+
| GROUP  | STREAM          | FILE                          | POSITION |
+--------+-----------------+-------------------------------+----------+
| group1 | group1_stream_0 | group1_stream_0_binlog.000017 |  7289205 |
| group1 | group1_stream_1 | group1_stream_1_binlog.000017 |  8122200 |
| group1 | group1_stream_2 | group1_stream_2_binlog.000017 |  5665953 |
+--------+-----------------+-------------------------------+----------+
3 rows in set (0.05 sec)
```
