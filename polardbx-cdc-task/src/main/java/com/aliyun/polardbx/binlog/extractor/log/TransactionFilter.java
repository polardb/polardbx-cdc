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
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_FILTER_TRANS_BLACKLIST;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
public class TransactionFilter {
    private static final Logger SKIP_TRANS_LOG = LoggerFactory.getLogger("SKIP_TRANS_LOG");
    private static final String SKIP_WHITE_LIST = getString(TASK_EXTRACT_FILTER_TRANS_BLACKLIST);

    public static boolean shouldFilter(TxnKey txnKey, String binlogFile, long pos) {
        if (StringUtils.isNotBlank(SKIP_WHITE_LIST)) {
            String[] array = StringUtils.split(SKIP_WHITE_LIST, "#");
            Set<String> sets = Sets.newHashSet(array);
            if (sets.contains(String.valueOf(txnKey.getTxnId())) ||
                sets.contains(Long.toHexString(txnKey.getTxnId()))) {
                SKIP_TRANS_LOG.info("skip transaction with tranId {}, groupId {}, binlog file {} , pos {}",
                    txnKey.getTxnId(), txnKey.getPartitionId(), binlogFile, pos);
                return true;
            }
        }
        return false;
    }
}
