/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.storage.Storage;

public class ExtractorBuilder {

    public static BinlogExtractor buildExtractor(BinlogParameter parameter, Storage storage, MergeSource mergeSource,
                                                 String rdsBinlogPath, long serverId) {
        BinlogExtractor extractor = new BinlogExtractor();
        extractor.init(parameter, rdsBinlogPath, serverId);
        DefaultOutputMergeSourceHandler logEventHandler = new DefaultOutputMergeSourceHandler(mergeSource, storage);
        extractor.setLogEventHandler(logEventHandler);
        return extractor;
    }

}
