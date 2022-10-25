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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.storage.Storage;

public class ExtractorBuilder {

    public static BinlogExtractor buildExtractor(BinlogParameter parameter, Storage storage, MergeSource mergeSource,
                                                 String rdsBinlogPath) {
        BinlogExtractor extractor = new BinlogExtractor();
        extractor.init(parameter, storage, rdsBinlogPath);
        DefaultOutputMergeSourceHandler logEventHandler = new DefaultOutputMergeSourceHandler(mergeSource, storage);
        extractor.setLogEventHandler(logEventHandler);
        return extractor;
    }

}
