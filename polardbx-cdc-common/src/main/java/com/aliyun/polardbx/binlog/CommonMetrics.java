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
package com.aliyun.polardbx.binlog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by ziyang.lb
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class CommonMetrics {
    private String key;//指标名称
    private String desc;//指标描述
    private int type;//1:Counter(只增加，上报的是delta),2:Gauge(瞬间值，上报的是当前值)
    private double value;

    public CommonMetrics var(Object value) {
        if (value instanceof Number) {
            return CommonMetrics.builder().key(key).type(type).value(((Number) value).doubleValue()).build();
        } else {
            return CommonMetrics.builder().key(key).type(type).value(-99).build();
        }
    }
}
