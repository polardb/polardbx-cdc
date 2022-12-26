
[English Version](../../README.md)

## 什么是 PolarDB-X CDC ?
PolarDB-X CDC 是云原生分布式数据库系统 [PolarDB-X](https://github.com/polardb/polardbx-sql) 的一个核心组件，负责全局增量日志的生成、分发和订阅。

通过 PolarDB-X CDC，PolarDB-X 数据库可以对外提供完全兼容 MySQL Binlog 格式和协议的增量日志，可实现与 MySQL Binlog 下游生态工具的无缝对接。

## 快速上手
PolarDB-X CDC 组件内置于 PolarDB-X 实例中，提供完全兼容 MySQL Binary Log 和 Replication 协议体验。

您可通过如下步骤快速体验 PolarDB-X CDC 的功能特性：

1. 启动一个 PolarDB-X 实例

参考 [快速启动文档](https://github.com/polardb/polardbx-sql#to-quick-start-with-polardb-x) 启动一个 PolarDB-X 实例。
   
2. 体验 MySQL Binlog 相关命令

PolarDB-X 完全兼容 MySQL Binlog 相关指令，例如 `SHOW BINARY LOGS`，`SHOW BINLOG EVENTS` 等，全部相关命令可参考 [MySQL 官方文档](https://dev.mysql.com/doc/refman/8.0/en/binary-log-formats.html) 。
   
3. 将 PolarDB-X 作为 MySQL 的备库

PolarDB-X 支持 MySQL Replica 相关指令，例如 `CHANGE MASTER TO`，`START/STOP/RESET SLAVE`，`SHOW SLAVE STATUS` 等，可将 PolarDB-X 直接作为 MySQL 的备库，命令详细说明请参考 [MySQL 官方文档](https://dev.mysql.com/doc/refman/8.0/en/change-master-to.html) 。
   
可在 [PolarDB-X 介绍页](https://github.com/polardb/polardbx-sql#quick-start) 查看更多文档。

## License
PolarDB-X CDC 采用 Apache License 2.0 协议，协议详情参看 [License](../../LICENSE) 文件。

## Contributing
查看 [如何贡献说明](https://github.com/polardb/polardbx-sql#contributing) 。




