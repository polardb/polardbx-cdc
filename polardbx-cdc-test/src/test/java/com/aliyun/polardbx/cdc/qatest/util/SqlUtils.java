/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
