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
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author ShuGuang
 */
@Slf4j
public class CommonMetricsHelper {
    private static final String TYPE = "平均值";
    private static final Map<String, CommonMetrics> DUMPER_M = Maps.newHashMap();
    private static final Map<String, CommonMetrics> DUMPER_S = Maps.newHashMap();
    private static final Map<String, CommonMetrics> TASK = Maps.newHashMap();
    private static final Map<String, CommonMetrics> ALL = Maps.newHashMap();

    private static final AtomicBoolean INITED = new AtomicBoolean(false);

    private static void init() {
        if (INITED.getAndSet(true) == true) {
            return;
        }
        List<String> lines;
        try {
            lines = FileUtils.readLines(
                new File(Resources.getResource("metrics.txt").getFile()), Charset.forName("UTF-8"));
            log.info("init metrics lines {}", lines);
            for (String line : lines) {
                String[] ss = StringUtils.split(line, "|");
                if (ss.length < 5) {
                    continue;
                }
                if (ss[0].contains("_dumper_m_")) {
                    DUMPER_M.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                if (ss[0].contains("_dumper_s_")) {
                    DUMPER_S.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                if (ss[0].contains("_task_")) {
                    TASK.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                ALL.put(ss[0], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                    .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
            }
            log.info("init metrics done DUMPER_M {}", DUMPER_M);
            log.info("init metrics done DUMPER_S {}", DUMPER_S);
            log.info("init metrics done TASK {}", TASK);
        } catch (IOException e) {
            log.error("prepare metrics fail", e);
        }

    }

    public static Map<String, CommonMetrics> getDumperM() {
        init();
        return DUMPER_M;
    }

    public static Map<String, CommonMetrics> getDumperS() {
        init();
        return DUMPER_S;
    }

    public static Map<String, CommonMetrics> getTask() {
        init();
        return TASK;
    }

    public static Map<String, CommonMetrics> getALL() {
        init();
        return ALL;
    }

    public static void main(String[] args) {
        System.out.println(DUMPER_M);
        System.out.println(getDumperM());
        System.out.println(DUMPER_M.size());
        System.out.println(getDumperM().size());
        System.out.println(getDumperM().size());

    }
}
