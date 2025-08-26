/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DescribeRdsBinlogListApi {

    public static List<BinlogFile> describeBinlogFiles(
        String dbInstanceName,
        String uid,
        String bid,
        long begin,
        long end,
        Integer maxRecordsPerPage,
        boolean useDbsApi) throws Exception {
        List<BinlogFile> totalRecords = new ArrayList<>();
        int counts = 0;
        int pageNumber = 1;
        do {
            if (useDbsApi) {
                DescribeUnifyArchiveLogFilesResult
                    result =
                    DbsApi.describeUnifyArchiveLogFiles(dbInstanceName, uid, bid, begin, end, maxRecordsPerPage,
                        pageNumber);
                totalRecords.addAll(result.getData().getContent().stream().map(BinlogFile::createFrom)
                    .collect(Collectors.toList()));
                counts += result.getData().getPageSize();
                if (result.getData().getTotalElements() <= counts) {
                    break;
                }
            } else {
                DescribeBinlogFilesResult result =
                    RdsApi.describeBinlogFiles(dbInstanceName, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                        RdsApi.formatUTCTZ(new Date(end)), maxRecordsPerPage, pageNumber++);
                totalRecords.addAll(result.getItems());
                counts += result.getItemsNumbers();
                if (result.getTotalRecords() <= counts) {
                    break;
                }
            }
        } while (true);
        return totalRecords;
    }
}
