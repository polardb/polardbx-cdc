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
