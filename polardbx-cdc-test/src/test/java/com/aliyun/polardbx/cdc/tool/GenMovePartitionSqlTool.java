/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.tool;

public class GenMovePartitionSqlTool {

    public static void main(String[] args) {
        String s = " pxc-xdb-s-xxx \n"
            + " pxc-xdb-s-yyy \n"
            + " pxc-xdb-s-xxx";

        String[] split = s.split("\n");
        for (int i = 1; i <= 64; i = i + 2) {
            String px = "p" + i;
            String py = "p" + (i + 1);
            String dn = split[i / 2].trim();
            String sqlT = "alter tablegroup tg14 move partitions %s,%s to '%s';";
            String sql = String.format(sqlT, px, py, dn);
            System.out.println(sql);
        }
    }
}
