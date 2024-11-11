/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * created by ziyang.lb
 */
public class PropertiesUtil {

    private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);

    public static Properties configProp = PropertiesUtil.parserProperties(ConfigConstant.CONN_CONFIG);

    public static Properties parserProperties(String path) {
        Properties serverProps = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            if (in != null) {
                serverProps.load(in);
            }
            return serverProps;
        } catch (IOException e) {
            log.error("parser the file: " + path, e);
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public static List<List<String>> read(String filePath) throws IOException {
        List<List<String>> result = new ArrayList<>();
        List<String> fileContent =
            FileUtils.readLines(new File(filePath));

        for (String oneLine : fileContent) {
            String[] fields = oneLine.substring(1).split("\\|");
            List<String> resultOneline = new ArrayList<>();
            for (String field : fields) {
                String trim = field.trim();
                if ("NULL".equalsIgnoreCase(trim)) {
                    resultOneline.add(null);
                } else {
                    resultOneline.add(trim);
                }
            }
            result.add(resultOneline);
        }
        return result;
    }

    public static boolean isStrictType() {
        return Boolean.parseBoolean(configProp.getProperty("strictTypeTest", "false"));
    }

    public static String polardbXDBName1(boolean part) {
        if (part) {
            return polardbXAutoDBName1();
        } else {
            return polardbXShardingDBName1();
        }
    }

    public static String polardbXDBName2(boolean part) {
        if (part) {
            return polardbXAutoDBName2();
        } else {
            return polardbXShardingDBName2();
        }
    }

    public static String polardbXShardingDBName1() {
        return String.valueOf(configProp.getProperty("polardbxDb", "drds_polarx1_qatest_app"));
    }

    public static String polardbXShardingDBName2() {
        return String.valueOf(configProp.getProperty("polardbxDb2", "drds_polarx2_qatest_app"));
    }

    public static String polardbXAutoDBName1() {
        return String.valueOf(configProp.getProperty("polardbxNewDb", "drds_polarx1_part_qatest_app"));
    }

    public static String polardbXAutoDBName2() {
        return String.valueOf(configProp.getProperty("polardbxNewDb2", "drds_polarx2_part_qatest_app"));
    }

    public static String mysqlDBName1() {
        return String.valueOf(configProp.getProperty("mysqlDb", "andor_qatest_polarx1"));
    }

    public static String mysqlDBName2() {
        return String.valueOf(configProp.getProperty("mysqlDb2", "andor_qatest_polarx2"));
    }

    public static String getConnectionProperties(boolean isMysql) {
        String connProp = configProp.getProperty("connProperties",
            "allowMultiQueries=true&rewriteBatchedStatements=true&characterEncoding=utf-8");
        if (isMysql) {
            return connProp.replace("&sessionVariables=polardbx_server_id=181818", "");
        } else {
            return connProp;
        }
    }

    public static int getCompareDetailParallelism() {
        return Integer.parseInt(configProp.getProperty("compareDetailParallelism", "8"));
    }

    public static boolean isMySQL80() {
        String drdsVersion = configProp.getProperty("drdsVersion", "5.7");
        return drdsVersion != null && drdsVersion.equalsIgnoreCase("8.0");
    }

    public static String getCdcCheckDbBlackList() {
        return configProp.getProperty("cdcCheckDbBlackList", "");
    }

    public static String getCdcCheckTableBlackList() {
        return configProp.getProperty("cdcCheckTableBlackList", "");
    }

    public static String getCdcCheckTableWhiteList() {
        String str = configProp.getProperty("cdcCheckTableWhiteList", "");
        // 表名可能有中文
        return new String(str.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    public static boolean enableAsyncDDL = Boolean.parseBoolean(configProp.getProperty("enableAsyncDDL", "true"));

    public static String getMetaDB =
        configProp.getProperty(ConfigConstant.META_DB, "polardbx_meta_db_polardbx");

    public static boolean usePrepare() {
        return getConnectionProperties(false).contains("useServerPrepStmts=true");
    }

    public static boolean useSSL() {
        return getConnectionProperties(false).contains("useSSL=true");
    }

    public static boolean isReplicaTest() {
        String value = System.getProperty("replica_test");
        return StringUtils.equalsIgnoreCase(value, "true");
    }

    public static final boolean useDruid = Boolean.parseBoolean(configProp.getProperty("useDruid", "false"));

    public static final Integer dnCount = Integer.valueOf(configProp.getProperty("dnCount", "1"));

    public static final boolean usingBinlogX = Boolean.parseBoolean(configProp.getProperty("usingBinlogX", "false"));

    public static final Integer shardDbCountEachDn = Integer.valueOf(configProp.getProperty("shardDbCountEachDn", "4"));
}
