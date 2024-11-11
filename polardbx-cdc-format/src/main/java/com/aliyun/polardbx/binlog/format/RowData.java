/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import lombok.Data;

import java.util.List;

@Data
public class RowData {

    private List<Field> biFieldList;

    private List<Field> aiFieldList;

    private BitMap biNullBitMap;

    private BitMap aiNullBitMap;

}
