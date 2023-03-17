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
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogStorageSequenceDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogStorageSequenceMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogStorageSequence;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class StorageSequence {
    private final static LoadingCache<String, String> SEQ_CACHE =
        CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
            @Override
            @ParametersAreNonnullByDefault
            public String load(String storageInstId) {
                return loadInternal(storageInstId);
            }
        });

    public static String getFixedLengthStorageSeq(String storageInstId) {
        if (StringUtils.isBlank(storageInstId)) {
            return "000000";
        } else {
            return SEQ_CACHE.getUnchecked(storageInstId);
        }
    }

    private static String loadInternal(String storageInstId) {
        // 兼容老版本的逻辑，优先使用hashcode，当出现冲突后再随机生成一个
        long hashCode = generateHashCode(storageInstId, "");
        while (true) {
            try {
                insert(storageInstId, hashCode);
                break;
            } catch (DataIntegrityViolationException e) {
                // 出现冲突有两种可能，storage_inst_id冲突，或者storage_seq冲突
                // 如果是前者，直接退出循环即可；如果是后者，说明不同storage出现了hash碰撞，需要重新生成一个新的hashCode
                BinlogStorageSequence sequence = get(storageInstId);
                if (sequence != null) {
                    break;
                } else {
                    hashCode = generateHashCode(storageInstId, UUID.randomUUID().toString());
                }
            }
        }
        BinlogStorageSequence sequence = get(storageInstId);
        return StringUtils.leftPad(sequence.getStorageSeq() + "", 6, "0");
    }

    private static long generateHashCode(String storageInstId, String seed) {
        long hashCode = Math.abs(Objects.hash(storageInstId + seed));
        String hashCodeStr = String.valueOf(hashCode);
        if (hashCodeStr.length() > 6) {
            hashCodeStr = hashCodeStr.substring(0, 6);
            hashCode = Long.parseLong(hashCodeStr);
        }
        return hashCode;
    }

    private static void insert(String storageInstId, Long storageSeq) {
        BinlogStorageSequenceMapper mapper = SpringContextHolder.getObject(BinlogStorageSequenceMapper.class);
        BinlogStorageSequence sequence = new BinlogStorageSequence();
        sequence.setStorageInstId(storageInstId);
        sequence.setStorageSeq(storageSeq);
        mapper.insert(sequence);
    }

    private static BinlogStorageSequence get(String storageInstId) {
        BinlogStorageSequenceMapper mapper = SpringContextHolder.getObject(BinlogStorageSequenceMapper.class);
        Optional<BinlogStorageSequence> optional = mapper
            .selectOne(s -> s.where(BinlogStorageSequenceDynamicSqlSupport.storageInstId, isEqualTo(storageInstId)));
        return optional.orElse(null);
    }
}
