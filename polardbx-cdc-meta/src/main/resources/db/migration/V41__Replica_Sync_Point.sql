CREATE TABLE IF NOT EXISTS rpl_sync_point
(
    `id`                      bigint(20) unsigned            not null auto_increment,
    `primary_tso`             varchar(128)                   not null,
    `secondary_tso`           varchar(128)                   not null,
    `create_time`             datetime                       not null default current_timestamp,
    primary key(`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;