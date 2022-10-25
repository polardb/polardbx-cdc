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
package com.aliyun.polardbx.cdc.qatest.base;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by ShuGuang
 */
public class ResultSetComparator implements Comparator<List<Map<String, Object>>> {
    @Override
    public int compare(List<Map<String, Object>> o1, List<Map<String, Object>> o2) {
        if (o1.size() != o2.size()) {
            return o1.size() - o2.size();
        }
        for (int i = 0; i < o1.size(); i++) {
            Map<String, Object> m1 = o1.get(i);
            Map<String, Object> m2 = o2.get(i);
            if (m1.size() != m2.size()) {
                return m1.size() - m2.size();
            }
            Iterator<Entry<String, Object>> iterator = m1.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, Object> next = iterator.next();
                Object v1 = next.getValue();
                Object v2 = m2.get(next.getKey());
                if (v1 == null ^ v2 == null) {
                    return -1;
                } else if (v1 == null && v2 == null) {

                } else {
                    if (v1 instanceof byte[] && v2 instanceof byte[]) {
                        if (!Arrays.equals((byte[]) v1, (byte[]) v2)) {
                            return -1;
                        }
                    } else {
                        if (!v1.equals(v2)) {
                            return -1;
                        }
                    }
                }
            }
        }
        return 0;
    }
}
