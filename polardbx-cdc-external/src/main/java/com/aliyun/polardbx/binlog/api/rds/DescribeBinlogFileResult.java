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
