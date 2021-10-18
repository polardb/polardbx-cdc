
[![LICENSE](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://github.com/ApsaraDB/galaxysql/blob/main/LICENSE)
[![Language](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com/)

[中文文档](docs/zh_CN/README.md)

## What is ApsaraDB GalaxyCDC ?
GalaxyCDC is a core component of [PolarDB-X](https://github.com/ApsaraDB/galaxysql) which is responsible for global binary log generation, publication and subscription.

With GalaxyCDC, PolarDB-X database can provide binary log fully compatible with MySQL binary log, which can be consumed seamlessly by MySQL binary log tools.
And it can play the role of MySQL slave through MySQL Replication protocol.

## Quick Start
The GalaxyCDC is a builtin component of the PolarDB-X.

Try GalaxyCDC by following these steps:

1. Start a PolarDB-X

See [Quick Start](https://github.com/ApsaraDB/galaxysql#to-quick-start-with-polardb-x) to start a PolarDB-X.
   
2. Try MySQL Binary Log Commands

PolarDB-X is fully compatible with MySQL binary log related commands, such as `SHOW BINARY LOGS`, `SHOW BINLOG EVENTS`, etc. All commands can be found in the [official MySQL documentation](https://dev.mysql.com/doc/refman/8.0/en/binary-log-formats.html).
   
3. PolarDB-X as MySQL Slave (WIP)

PolarDB-X supports MySQL `CHANGE MASTER TO` command, you can use PolarDB-X as MySQL slave, refer to MySQL [official document](https://dev.mysql.com/doc/refman/8.0/en/change-master-to.html) for command details.

See [more docs](https://github.com/ApsaraDB/galaxysql#quick-start) about PolarDB-X.

## License
ApsaraDB GalaxyCDC is under Apache License 2.0, see the [license](LICENSE) for details.

## Contributing
See [how to contribute](https://github.com/ApsaraDB/galaxysql#contributing).




