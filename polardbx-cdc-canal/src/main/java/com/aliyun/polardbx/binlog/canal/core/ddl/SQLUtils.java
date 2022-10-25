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
