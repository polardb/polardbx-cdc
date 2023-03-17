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
package com.aliyun.polardbx.binlog.daemon.vo;

import lombok.Data;

/**
 * Created by ShuGuang
 */
@Data
public class MachineInfo {
    String cpu, mem, retrans;
    float bytin, bytout, load1;

    /**
     * @param tsar -C 指令返回的数据
     * @return 当前主机的监控信息
     */

    //@formatter:off
    //host	tsar	cpu:util=1.6 mem:util=41.5 tcp:retran=0.5 traffic:bytin=4512.0 traffic:bytout=14341.0 io:vda:util=0.2 io:vda1:util=0.2 load:load1=0.1
    //@formatter:on
    public MachineInfo from(String tsar) {
        final byte[] bytes = tsar.getBytes();
        final int length = bytes.length;

        int start = 0, stop = 0, eq = 0;

        for (int i = 0; i < length; i++) {
            final byte b = bytes[i];
            switch (b) {
            case '\t':
                start = i + 1;
                break;
            case ' ':
            case '\n':
                stop = i;
                //convert it
                convert(new String(bytes, start, eq - start), new String(bytes, eq + 1, stop - eq));
                start = i + 1;
                break;
            case '=':
                eq = i;
                break;
            default:

            }
        }
        return this;
    }

    private void convert(String key, String value) {
        switch (key) {
        case "cpu:util":
            this.cpu = value;
            break;

        case "mem:util":
            this.mem = value;
            break;

        case "tcp:retran":
            this.retrans = value;
            break;

        case "traffic:bytin":
            this.bytin = Float.valueOf(value);
            break;
        case "traffic:bytout":
            this.bytout = Float.valueOf(value);
            break;

        case "load:load1":
            this.load1 = Float.valueOf(value);
            break;
        default:
            break;
        }
    }

    public static void main(String[] args) {
        String s = "host\ttsar\tcpu:util=1.6 mem:util=41.5 tcp:retran=0.5 traffic:bytin=4512.0 traffic:bytout=14341.0"
            + " io:vda:util=0.2 io:vda1:util=0.2 load:load1=0.1\n";
        System.out.println(new MachineInfo().from(s));
    }
}
