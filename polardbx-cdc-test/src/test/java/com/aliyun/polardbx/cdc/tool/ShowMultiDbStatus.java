/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.tool;

public class ShowMultiDbStatus {

    public static void main(String[] args) {
        String s1 = "bolt_address\n"
            + "bolt_aha_mall\n"
            + "bolt_inventory\n"
            + "bolt_logistics\n"
            + "bolt_marketing\n"
            + "bolt_product\n"
            + "bolt_product_admin\n"
            + "bolt_promise\n"
            + "bolt_subscribe\n"
            + "bolt_tmp\n"
            + "bolt_trade";

        String s2 =
            "mysql -h127.1 -u%s -e 'show db status' |grep TOTAL | awk '{ sum += $5 } END { print sum }'";

        String[] array = s1.split("\n");
        for (String s : array) {
            System.out.println("echo " + s);
            System.out.printf((s2) + "%n", s);
        }
    }
}
