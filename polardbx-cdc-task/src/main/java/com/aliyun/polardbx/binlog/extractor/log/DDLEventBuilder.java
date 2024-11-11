/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import org.apache.commons.lang3.StringUtils;

public class DDLEventBuilder {
    /**
     * 所有对DDLEvent 以及内部的对象进行组装的逻辑，必须在这个方法里完成，否则在ddl rebuild 的逻辑中，配置优先使用__cdc_ddl_record__的逻辑会出现数据不正确的问题。
     */
    public static DDLEvent build(String id, String jobId, String schemaName, String tableName, String sqlKind,
                                 String ddl, int visibility, String ext, String metaInfo) {
        DDLExtInfo ddlExtInfo = null;
        if (StringUtils.isNotBlank(ext)) {
            ddlExtInfo = DDLExtInfo.parseExtInfo(ext);
        }

        DDLRecord ddlRecord =
            DDLRecord.builder().id(Long.valueOf(id)).jobId(StringUtils.isBlank(jobId) ? null : Long.valueOf(jobId))
                .ddlSql(ddl).sqlKind(sqlKind).schemaName(StringUtils.lowerCase(schemaName))
                .tableName(StringUtils.lowerCase(tableName)).visibility(visibility).metaInfo(metaInfo)
                .extInfo(ddlExtInfo).build();

        DDLEvent ddlEvent = new DDLEvent();
        ddlEvent.setDdlRecord(ddlRecord);
        ddlEvent.setExt(ext);
        ddlEvent.initVisible(visibility, ddlExtInfo);
        return ddlEvent;
    }
}
