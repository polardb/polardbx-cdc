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
package com.aliyun.polardbx.cdc.qatest.util;

import com.google.common.collect.Lists;

/**
 * created by ziyang.lb
 **/
public class SqlUtils {

    public static String printBytes(byte[] bs) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("(");
        byte[] arr$ = bs;
        int len$ = bs.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            byte b = arr$[i$];
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }

            sb.append("0x").append(Integer.toHexString(b & 255));
        }

        sb.append(")");
        return sb.toString();
    }

    public static void main(String args[]) {
        String dbName = "xxx";
        String tableName = "yyy";
        StringBuilder builder = new StringBuilder();

        builder.append("select md5(data) md5 from ").append("(")
            .append("select group_concat(")
            .append("id,")
            .append("'->',")
            .append(Lists.newArrayList())
            .append(") as data ")
            .append("from ( select * from ")
            .append("`" + dbName + "`.")
            .append("`" + tableName + "` ")
            .append("order by id asc")
            .append(") t) tt");
        System.out.println(builder.toString());
    }
}
