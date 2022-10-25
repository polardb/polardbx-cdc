/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.cdc.qatest.base;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        return Boolean.valueOf(configProp.getProperty("strictTypeTest", "false"));
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

    public static String getConnectionProperties() {
        return configProp
            .getProperty("connProperties",
                "allowMultiQueries=true&rewriteBatchedStatements=true&characterEncoding=utf-8");
    }

    public static boolean isMySQL80() {
        String drdsVersion = configProp.getProperty("drdsVersion", "5.7");
        return drdsVersion != null && drdsVersion.equalsIgnoreCase("8.0");
    }

    public static boolean enableAsyncDDL = Boolean.valueOf(configProp.getProperty("enableAsyncDDL", "true"));

    public static String getMetaDB =
        configProp.getProperty(ConfigConstant.META_DB, "polardbx_meta_db_polardbx");

    public static boolean usePrepare() {
        return getConnectionProperties().contains("useServerPrepStmts=true");
    }

    public static boolean useSSL() {
        return getConnectionProperties().contains("useSSL=true");
    }

    public static final boolean useDruid = Boolean.valueOf(configProp.getProperty("useDruid", "false"));

    public static final Integer dnCount = Integer.valueOf(configProp.getProperty("dnCount", "1"));

    public static final boolean usingBinlogX = Boolean.parseBoolean(configProp.getProperty("usingBinlogX", "false"));

    public static final Integer shardDbCountEachDn = Integer.valueOf(configProp.getProperty("shardDbCountEachDn", "4"));
}
