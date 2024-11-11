/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
