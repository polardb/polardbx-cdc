/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.tool;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * created by ziyang.lb
 * 解析binlog，并生成SQL，WIP
 **/
@Slf4j
public class SQLFlashBackTool {
    private static final Map<String, StringBuilder> fileMap = new HashMap<>();

    @SneakyThrows
    public static void main(String[] args) throws IOException, SQLException {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("testing-conf/spring-test.xml");
        appContextBootStrap.boot();
        DataSource dataSource = SpringContextHolder.getObject("h2DataSource");

        //Set<String> set = getTableList("/Users/lubiao/Util/table_id_5654.log");
        //print(set);

        /*Set<String> set1 = getTableList("/Users/lubiao/Util/table_id_1500.log");
        Set<String> set2 = getTableList("/Users/lubiao/Util/table_id_1501.log");
        set2.addAll(set1);
        print(set2);*/
    }

    private static void generateSql(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;

        String tableName = "";
        int pos = 0;
        boolean insertFlag = false;

        boolean updateFlag = false;
        boolean updateBegin = false;
        boolean updateEnd;

        boolean deleteFlag = false;
        boolean deleteBegin;
        boolean deleteEnd;

        while ((s = bufferedReader.readLine()) != null) {
            if (StringUtils.startsWithIgnoreCase(s, "### INSERT INTO")) {
                insertFlag = true;
                tableName = StringUtils.substringAfter(s, "### INSERT INTO ");
                fileMap.putIfAbsent(tableName, new StringBuilder());
                fileMap.get(tableName).append("\n").append("INSERT INTO ").append(tableName).append("SELECT ");
            } else if (StringUtils.startsWithIgnoreCase(s, "### UPDATE")) {
                updateFlag = true;
            } else if (StringUtils.startsWithIgnoreCase(s, "### DELETE FROM")) {
                deleteFlag = true;
            } else {
                if (insertFlag) {
                    if (pos == 0) {
                        pos++;
                    } else {
                        if (StringUtils.startsWithIgnoreCase(s, "###   @")) {
                            appendOneColumn(pos, tableName, s);
                            pos++;
                        } else {
                            fileMap.get(tableName).append(");");
                            insertFlag = false;
                            pos = 0;
                        }
                    }
                } else if (updateFlag) {
                    if (updateBegin) {
                        if (StringUtils.startsWithIgnoreCase(s, "###   @")) {
                            appendOneColumn(pos, tableName, s);
                            pos++;
                        } else {
                            fileMap.get(tableName).append(");");
                            updateFlag = false;
                            updateBegin = false;
                            pos = 0;
                        }
                    } else {
                        if (StringUtils.startsWithIgnoreCase(s, "### SET")) {
                            updateBegin = true;
                        }
                    }
                } else if (deleteFlag) {

                } else {

                }
            }
        }
    }

    private static void appendOneColumn(int pos, String tableName, String s) {
        if (pos != 1) {
            fileMap.get(tableName).append(",");
        }
        s = StringUtils.substringAfter(s, "=");
        s = StringUtils.substringBefore(s, "/*").trim();
        fileMap.get(tableName).append(s).append(",");
    }

    private static void print(Set<String> set) {
        for (String s1 : set) {
            String[] array = StringUtils.split(s1, ".");
            if ("`mysql`".equalsIgnoreCase(array[0])) {
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(array[0], "`__cdc_")) {
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(array[1], "`g_i_")) {
                continue;
            }
            if ("`__drds_global_tx_log`".equalsIgnoreCase(array[1])) {
                continue;
            }
            System.out.println(s1);
        }
    }

    private static Set<String> getTableList(String fileStr) throws IOException {
        File file = new File(fileStr);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;

        Set<String> set = new TreeSet<>();
        while ((s = bufferedReader.readLine()) != null) {
            String originStr = s;
            try {
                if (StringUtils.contains(s, "INSERT") || StringUtils.contains(s, "UPDATE") || StringUtils
                    .contains(s, "DELETE")) {
                    s = StringUtils.substringAfter(s, " `");
                    s = "`" + s;
                    set.add(s);
                }
            } catch (Throwable t) {
                System.err.println(originStr);
            }

        }

        return set;
    }
}
