/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

public class GridEvent {

    public static int ENCODED_FLAG_LENGTH = 1;
    public static int ENCODED_SID_LENGTH = 16;  // Uuid::BYTE_LENGTH;
    public static int ENCODED_GNO_LENGTH = 8;
    /// Length of typecode for logical timestamps.
    public static int LOGICAL_TIMESTAMP_TYPECODE_LENGTH = 1;
    /// Length of two logical timestamps.
    public static int LOGICAL_TIMESTAMP_LENGTH = 16;
    // Type code used before the logical timestamps.
    public static int LOGICAL_TIMESTAMP_TYPECODE = 2;
    /// Total length of post header
    public static int POST_HEADER_LENGTH = ENCODED_FLAG_LENGTH +               /* flags */
        ENCODED_SID_LENGTH +                /* SID length */
        ENCODED_GNO_LENGTH +                /* GNO length */
        LOGICAL_TIMESTAMP_TYPECODE_LENGTH + /* length of typecode */
        LOGICAL_TIMESTAMP_LENGTH;           /* length of two logical timestamps */
}
