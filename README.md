
[![LICENSE](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://github.com/polardb/polardbx-sql/blob/main/LICENSE)
[![Language](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com/)

[中文文档](docs/zh_CN/README.md)

## What is PolarDB-X CDC ?
PolarDB-X CDC is a core component of [PolarDB-X](https://github.com/polardb/polardbx-sql) which is responsible for global binary log generation, publication and subscription.

With PolarDB-X CDC, PolarDB-X database can provide binary log fully compatible with MySQL binary log, which can be consumed seamlessly by MySQL binary log tools.
And it can play the role of MySQL slave through MySQL Replication protocol.

## Quick Start
The PolarDB-X CDC is a builtin component of the PolarDB-X.

Try PolarDB-X CDC by following these steps:

1. Start a PolarDB-X

See [Quick Start](https://github.com/polardb/polardbx-sql#to-quick-start-with-polardb-x) to start a PolarDB-X.
   
2. Try MySQL Binary Log Commands

PolarDB-X is fully compatible with MySQL binary log related commands, such as `SHOW BINARY LOGS`, `SHOW BINLOG EVENTS`, etc. All commands can be found in the [official MySQL documentation](https://dev.mysql.com/doc/refman/8.0/en/binary-log-formats.html).
   
3. PolarDB-X as MySQL Slave

PolarDB-X supports MySQL Replica related commands, such as `CHANGE MASTER TO`，`START/STOP/RESET SLAVE`，`SHOW SLAVE STATUS`, etc. you can use PolarDB-X as MySQL slave, refer to  [Replica Reference Manual](https://github.com/polardb/polardbx-cdc/tree/main/polardbx-cdc-rpl/README.md) for command details.

See [more docs](https://github.com/polardb/polardbx-sql#quick-start) about PolarDB-X.

## License
PolarDB-X CDC is under Apache License 2.0, see the [license](LICENSE) for details.

## Contributing
See [how to contribute](https://github.com/polardb/polardbx-sql#contributing).
