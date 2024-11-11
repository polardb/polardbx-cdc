/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:52
 * @since 3.2.6
 */
public class DescribeBinlogFileResult {
    private RdsItem Items;
    private long PageNumber;
    private long TotalRecordCount;
    private long TotalFileSize;
    private String RequestId;
    private long PageRecordCount;

    public RdsItem getItems() {
        return Items;
    }

    public void setItems(RdsItem items) {
        Items = items;
    }

    public long getPageNumber() {
        return PageNumber;
    }

    public void setPageNumber(long pageNumber) {
        PageNumber = pageNumber;
    }

    public long getTotalRecordCount() {
        return TotalRecordCount;
    }

    public void setTotalRecordCount(long totalRecordCount) {
        TotalRecordCount = totalRecordCount;
    }

    public long getTotalFileSize() {
        return TotalFileSize;
    }

    public void setTotalFileSize(long totalFileSize) {
        TotalFileSize = totalFileSize;
    }

    public String getRequestId() {
        return RequestId;
    }

    public void setRequestId(String requestId) {
        RequestId = requestId;
    }

    public long getPageRecordCount() {
        return PageRecordCount;
    }

    public void setPageRecordCount(long pageRecordCount) {
        PageRecordCount = pageRecordCount;
    }
}
