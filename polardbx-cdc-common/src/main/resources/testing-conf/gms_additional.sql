DROP TABLE IF EXISTS `inst_config`;
CREATE TABLE `inst_config`
(
    `id`           bigint(11) NOT NULL AUTO_INCREMENT,
    `gmt_created`  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `inst_id`      varchar(128)  NOT NULL,
    `param_key`    varchar(128)  NOT NULL,
    `param_val`    varchar(1024) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inst_id_key` (`inst_id`,`param_key`)
) ENGINE=InnoDB AUTO_INCREMENT=654 DEFAULT CHARSET=utf8;