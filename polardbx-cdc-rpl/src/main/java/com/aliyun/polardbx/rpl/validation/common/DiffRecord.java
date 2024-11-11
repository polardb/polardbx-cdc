/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.common;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

/**
 * Represent a table record
 *
 * @author siyu.yusi
 */
@Data
@Builder
public class DiffRecord {
    private List<String> keys;
    private List<Object> srcKeyVal;
    private List<Object> dstKeyVal;
    private DiffType diffType;

    @Getter
    public enum DiffType {
        DIFF,
        ORPHAN,
        MISS
    }

}
