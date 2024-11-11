/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl;

import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.List;

public class SQLUtils {

    public static void main(String[] args) {
        System.out.println(removeDDLHints(
            "/* applicationname=datagrip 2021.2.4 */ create database /*!32312 if not exists*/ `slt_single` /*!40100 default character set utf8mb4 */"));
    }

    /**
     * 去掉mysql ddl中的hint，fastsql会报错
     */
    public static String removeDDLHints(String ddl) {
        // fast sql 遇到这种Hint会报错，过滤掉/* applicationname=datagrip 2021.2.4 */ create database /*!32312 if not exists*/ `slt_single` /*!40100 default character set utf8mb4 */
        List<MutablePair<Integer, Integer>> hintPairList = new ArrayList<>();
        final int len = ddl.length();
        MutablePair<Integer, Integer> pair = null;
        for (int i = 0; i < len; i++) {
            char c = ddl.charAt(i);
            if (c == '/') {
                c = ddl.charAt(++i);
                if (c == '*') {
                    pair = new MutablePair<>();
                    pair.setLeft(i - 1);
                    continue;
                }
            }
            if (c == '*') {
                c = ddl.charAt(++i);
                if (c == '/') {
                    if (pair != null) {
                        pair.setRight(i + 1);
                        hintPairList.add(pair);
                        pair = null;
                    }
                }
            }
        }

        int decrease = 0;
        for (MutablePair<Integer, Integer> p : hintPairList) {
            int b = p.left - decrease;
            int e = p.right - decrease;
            decrease += (p.right - p.left);
            ddl = ddl.substring(0, b) + ddl.substring(e);
        }
        return ddl;
    }
}
