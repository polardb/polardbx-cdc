[English Version](../../README.md)

## 什么是 PolarDB-X CDC ?

PolarDB-X CDC 是云原生分布式数据库系统 [PolarDB-X](https://github.com/polardb/polardbx-sql) 的一个核心组件，负责全局增量日志的生成、分发和订阅。

通过 PolarDB-X CDC，PolarDB-X 数据库可以对外提供完全兼容 MySQL Binlog 格式和协议的增量日志，可实现与 MySQL Binlog
下游生态工具的无缝对接。

PolarDB-X CDC的核心功能主要包括以下几点：

* **全局Binlog**（Global Binlog）：将所有 PolarDB-X DN 节点产生的原始 Binlog
  归并到同一个全局队列，提供了保证事务完整性和有序性的日志流，可以提供更高强度的数据一致性保证。
* **Binlog多流**（Binlog-X）：解决大规模集群下单流binlog存在的单点瓶颈问题，实时生成多条逻辑日志流，提供更强的分布式扩展能力。
* **Replica**：兼容 MySQL Replication协议，提供作为 MySQL 或 PolarDB-X 备库的能力。

## 快速上手

PolarDB-X CDC 组件内置于 PolarDB-X 实例中，提供完全兼容 MySQL Binary Log 和 Replication 协议体验。

您可通过如下步骤快速体验 PolarDB-X CDC 的功能特性：

1. 启动一个 PolarDB-X 实例

参考 [CDC节点创建](https://doc.polardbx.com/operator/ops/component/cdc/1-create-cdc-node-example.html) 创建一个带有 CDC
节点的 PolarDB-X 实例。

2. 体验 MySQL Binlog 相关命令

PolarDB-X 完全兼容 MySQL Binlog 相关指令，例如 `SHOW BINARY LOGS`，`SHOW BINLOG EVENTS`
等，全部相关命令可参考 [Binlog相关命令介绍](./binlog-commands-intro.md) 。

3. 将 PolarDB-X 作为 MySQL 的备库

PolarDB-X 支持 MySQL Replica 相关指令，例如 `CHANGE MASTER TO`，`START/STOP/RESET SLAVE`，`SHOW SLAVE STATUS` 等，可将
PolarDB-X 直接作为 MySQL
的备库，命令详细说明请参考 [MySQL 官方文档](https://dev.mysql.com/doc/refman/8.0/en/change-master-to.html) 。

可在 [PolarDB-X 介绍页](https://github.com/polardb/polardbx-sql#quick-start) 查看更多文档。

## License

PolarDB-X CDC 采用 Apache License 2.0 协议，协议详情参看 [License](../../LICENSE) 文件。

## Contributing

查看 [如何贡献说明](https://github.com/polardb/polardbx-sql#contributing) 。
