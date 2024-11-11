/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
