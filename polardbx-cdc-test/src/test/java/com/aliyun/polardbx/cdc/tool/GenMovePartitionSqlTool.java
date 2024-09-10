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
