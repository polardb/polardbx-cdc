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
package oss;

import org.junit.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SortTest {
    @Test
    public void test() {
        Map<String, Object> map = new HashMap<>();
        map.put("binlog.00010", new Object());
        map.put("binlog.00001", new Object());
        map.put("binlog.00002", new Object());
        map.put("binlog.00009", new Object());
        map.put("binlog.00008", new Object());
        map.put("binlog.00003", new Object());
        map.put("binlog.00004", new Object());
        map.put("binlog.00006", new Object());
        map.put("binlog.00005", new Object());
        map.put("binlog.00007", new Object());
        List<String> nameList =
            map.entrySet().stream().map(Map.Entry::getKey).sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        for (String n : nameList) {
            System.out.println(n);
        }
    }
}
