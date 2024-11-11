/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

public class JoinSqlTraceIdOrderTest extends RplBaseTestCase {

    // see : https://aone.alibaba-inc.com/v2/project/860366/bug/58961149
    @Test
    public void testJoinSqlTraceIdOrder() throws SQLException {
        try (Connection connection = getPolardbxConnection()) {
            JdbcUtil.executeUpdate(connection,
                String.format("create database if not exists %s mode = auto", CDC_COMMON_TEST_DB));

            JdbcUtil.executeUpdate(connection,
                "use " + CDC_COMMON_TEST_DB);

            JdbcUtil.executeUpdate(connection,
                "CREATE TABLE `t_jg_tag` (\n"
                    + "        `id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
                    + "        `tag_name` varchar(255) DEFAULT NULL COMMENT '便签名',\n"
                    + "        `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',\n"
                    + "        `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '版本号',\n"
                    + "        `created_by` varchar(20) DEFAULT NULL COMMENT '创建人',\n"
                    + "        `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                    + "        `updated_by` varchar(20) DEFAULT NULL COMMENT '更新人',\n"
                    + "        `updated_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
                    + "        PRIMARY KEY USING BTREE (`id`)\n"
                    + ") ENGINE = InnoDB AUTO_INCREMENT = 21 DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC\n"
                    + "SINGLE");

            JdbcUtil.executeUpdate(connection,
                "CREATE TABLE `t_user_jg_tag` (\n"
                    + "        `id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
                    + "        `phone` varchar(255) DEFAULT NULL,\n"
                    + "        `tag_id` bigint(20) DEFAULT NULL,\n"
                    + "        `res_id` varchar(255) DEFAULT NULL,\n"
                    + "        `alias` varchar(255) DEFAULT NULL,\n"
                    + "        `mobile` varchar(255) DEFAULT NULL,\n"
                    + "        `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',\n"
                    + "        `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '版本号',\n"
                    + "        `created_by` varchar(20) DEFAULT NULL COMMENT '创建人',\n"
                    + "        `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                    + "        `updated_by` varchar(20) DEFAULT NULL COMMENT '更新人',\n"
                    + "        `updated_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
                    + "        PRIMARY KEY USING BTREE (`id`)\n"
                    + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC\n"
                    + "SINGLE");

            JdbcUtil.executeUpdate(connection,
                "insert into t_jg_tag(id, tag_name, deleted, version, created_by, created_date, updated_by, updated_date)\n"
                    + "values (1, 'xyz', 0, 0, 'admin', now(), 'admin', now())");
            JdbcUtil.executeUpdate(connection,
                "insert into t_user_jg_tag(id, phone, tag_id, res_id, alias, mobile, deleted, version, created_by, created_date,updated_by,updated_date)\n"
                    + "values (2, '123', 1, '123', '123', '123', 0, 0, 'admin', now(), 'admin', now())");

            connection.setAutoCommit(false);

            JdbcUtil.executeQuery("select * from t_jg_tag LEFT JOIN t_user_jg_tag ON t_jg_tag.id = t_user_jg_tag.id",
                connection);
            JdbcUtil.executeQuery("select * from t_jg_tag LEFT JOIN t_user_jg_tag ON t_jg_tag.id = t_user_jg_tag.id",
                connection);
            JdbcUtil.executeUpdate(connection,
                "UPDATE t_jg_tag LEFT JOIN t_user_jg_tag ON t_jg_tag.id = t_user_jg_tag.id SET t_jg_tag.deleted = 1, t_user_jg_tag.deleted = 1 WHERE t_jg_tag.id = 1;\n");

            connection.commit();
        }
    }
}
