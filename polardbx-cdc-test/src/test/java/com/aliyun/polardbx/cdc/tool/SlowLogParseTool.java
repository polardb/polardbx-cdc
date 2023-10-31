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

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * created by ziyang.lb
 **/
public class SlowLogParseTool {
    private static final String LINE_SUFFIX = "tddl version: 5.1.27-1235481";

    public static void main(String[] args) throws IOException {
        File file = new File("/Users/lubiao/Downloads/1708_1715异常/20230510_slow.log");
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            String originStr = s;
            if (StringUtils.endsWith(s, LINE_SUFFIX)) {
                try {
                    String traceId = StringUtils.substringAfterLast(s, "#");

                    s = StringUtils.substringBeforeLast(s, "#");
                    String count = StringUtils.substringAfterLast(s, "#");

                    //耗时，单位ms
                    s = StringUtils.substringBeforeLast(s, "#");
                    String time = StringUtils.substringAfterLast(s, "#");

                    if (Long.parseLong(time) > 500000) {
                        System.out.println(time + " " + traceId);
                    }
                } catch (Throwable t) {
                    System.err.println(originStr);
                }
            }
        }
    }
}
