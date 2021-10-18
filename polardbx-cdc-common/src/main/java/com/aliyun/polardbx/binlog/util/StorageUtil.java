/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;

/**
 * Created by ziyang.lb
 **/
public class StorageUtil {
    public static String getConfiguredExpectedStorageTso() {
        return SystemDbConfig.getSystemDbConfig(EXPECTED_STORAGE_TSO_KEY);
    }

    public static String buildExpectedStorageTso(String startTso) {
        return StringUtils.isBlank(startTso) ? TaskConfig.ORIGIN_TSO : buildInternal(startTso);
    }

    private static String buildInternal(String startTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos =
            storageHistoryMapper.select(s -> s.where(tso, isLessThanOrEqualTo(startTso)));
        return storageHistoryInfos.stream().map(StorageHistoryInfo::getTso).max(Comparator.comparing(s -> s)).get();
    }
}
