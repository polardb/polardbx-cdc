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
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.dao.CdcSyncPointMetaMapper;
import com.aliyun.polardbx.binlog.domain.po.CdcSyncPointMeta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * @author yudong
 * @since 2024/4/28 14:45
 **/
@Service
public class CdcSyncPointMetaService {

    private static final String CDC_SYNC_POINT_META = "cdc_sync_point_meta";

    private static final String INSERT_IGNORE =
        "INSERT IGNORE INTO " + CDC_SYNC_POINT_META + " (id, valid) values (?, ?)";

    @Resource
    private CdcSyncPointMetaMapper mapper;

    @Resource(name = "metaJdbcTemplate")
    private JdbcTemplate metaJdbcTemplate;

    public Optional<CdcSyncPointMeta> selectById(String id) {
        return mapper.selectByPrimaryKey(id);
    }

    public int insertIgnore(String id) {
        return metaJdbcTemplate.update(INSERT_IGNORE, id, 0);
    }

}
