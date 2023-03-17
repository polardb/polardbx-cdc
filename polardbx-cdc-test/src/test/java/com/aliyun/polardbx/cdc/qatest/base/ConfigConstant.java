/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.cdc.qatest.base;

/**
 * created by ziyang.lb
 */
public class ConfigConstant {

    public static final String RESOURCE_PATH = ConfigConstant.class.getClassLoader().getResource(".").getPath();
    public static final String CONN_CONFIG = RESOURCE_PATH + "qatest.properties";

    public static final String SKIP_INIT_MYSQL = "skipInitDnMysql";
    public static final String DN_MYSQL_USER = "dnMysqlUserName";
    public static final String DN_MYSQL_PASSWORD = "dnMysqlPassword";
    public static final String DN_MYSQL_PORT = "dnMysqlPort";
    public static final String DN_MYSQL_ADDRESS = "dnMysqlAddr";

    public static final String DN_MYSQL_USER_SECOND = "dnMysqlUserNameSecond";
    public static final String DN_MYSQL_PASSWORD_SECOND = "dnMysqlPasswordSecond";
    public static final String DN_MYSQL_PORT_SECOND = "dnMysqlPortSecond";
    public static final String DN_MYSQL_ADDRESS_SECOND = "dnMysqlAddrSecond";

    public static final String POLARDBX_USER = "polardbxUserName";
    public static final String POLARDBX_PASSWORD = "polardbxPassword";
    public static final String POLARDBX_PORT = "polardbxPort";
    public static final String POLARDBX_ADDRESS = "polardbxAddr";

    public static final String META_DB = "metaDbName";
    public static final String META_USER = "metaDbUser";
    public static final String META_PASSWORD = "metaDbPasswd";
    public static final String META_PORT = "metaPort";
    public static final String META_ADDRESS = "metaDbAddr";

    public static final String CDC_SYNC_DB_USER = "cdcSyncDbUser";
    public static final String CDC_SYNC_DB_PASSWORD = "cdcSyncDbPasswd";
    public static final String CDC_SYNC_DB_PORT = "cdcSyncDbPort";
    public static final String CDC_SYNC_DB_ADDRESS = "cdcSyncDbAddr";

    public static final String CDC_SYNC_DB_USER_FIRST = "cdcSyncDbUserFirst";
    public static final String CDC_SYNC_DB_PASSWORD_FIRST = "cdcSyncDbPasswdFirst";
    public static final String CDC_SYNC_DB_PORT_FIRST = "cdcSyncDbPortFirst";
    public static final String CDC_SYNC_DB_ADDRESS_FIRST = "cdcSyncDbAddrFirst";

    public static final String CDC_SYNC_DB_USER_SECOND = "cdcSyncDbUserSecond";
    public static final String CDC_SYNC_DB_PASSWORD_SECOND = "cdcSyncDbPasswdSecond";
    public static final String CDC_SYNC_DB_PORT_SECOND = "cdcSyncDbPortSecond";
    public static final String CDC_SYNC_DB_ADDRESS_SECOND = "cdcSyncDbAddrSecond";

    public static final String CDC_SYNC_DB_USER_THIRD = "cdcSyncDbUserThird";
    public static final String CDC_SYNC_DB_PASSWORD_THIRD = "cdcSyncDbPasswdThird";
    public static final String CDC_SYNC_DB_PORT_THIRD = "cdcSyncDbPortThird";
    public static final String CDC_SYNC_DB_ADDRESS_THIRD = "cdcSyncDbAddrThird";

    public static final String URL_PATTERN = "jdbc:mysql://%s:%s?";
    public static final String URL_PATTERN_WITH_DB = "jdbc:mysql://%s:%s/%s?";

    public static final String CDC_WAIT_TOKEN_TIMEOUT_MINUTES = "cdcWaitTokenTimeOutMinute";

}
