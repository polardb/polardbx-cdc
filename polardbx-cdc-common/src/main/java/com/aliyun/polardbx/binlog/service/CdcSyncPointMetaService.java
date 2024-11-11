/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
