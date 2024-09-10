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

import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DDL_ALTER_IMPLICIT_TABLE_GROUP_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DDL_ALTER_MANUALLY_TABLE_GROUP_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

@EqualsAndHashCode
public class DDLEvent implements HandlerEvent {

    private DDLRecord ddlRecord;
    private boolean visibleToPolardbX;
    private boolean visibleToMysql;
    private boolean visible;
    private String ext;
    private BinlogPosition position;
    private QueryEventBuilder queryEventBuilder;
    private String commitKey;
    private byte[] data;

    public void initVisible(int value, DDLExtInfo ddlExtInfo) {
        if (value == 1) {
            // Public
            visibleToPolardbX = true;
            visibleToMysql = true;
        } else if (value == 2) {
            // Protected
            visibleToPolardbX = true;
            visibleToMysql = false;

            if ("ALTER_TABLEGROUP".equals(ddlRecord.getSqlKind())) {
                visibleToPolardbX = supportAlterTg(ddlExtInfo);
            }
        } else if (value == 0) {
            // Private
            visibleToPolardbX = false;
            visibleToMysql = false;
        } else {
            throw new PolardbxException("invalid visibility : " + value);
        }
        visible = visibleToMysql || visibleToPolardbX;
    }

    private boolean supportAlterTg(DDLExtInfo ddlExtInfo) {
        boolean supportAlterManuallyTg = getBoolean(BINLOG_DDL_ALTER_MANUALLY_TABLE_GROUP_ENABLED);
        boolean supportAlterImplicitTg = getBoolean(BINLOG_DDL_ALTER_IMPLICIT_TABLE_GROUP_ENABLED);
        if (ddlExtInfo != null && ddlExtInfo.getManuallyCreatedTableGroup() != null) {
            // 能进入if，说明是支持GDN的CN(支持GDN的CN版本新增了manuallyCreatedTableGroup属性)，否则直接返回false
            boolean isManuallyCreateTg = ddlExtInfo.getManuallyCreatedTableGroup();
            boolean enableImplicitTgOfCN = ddlExtInfo.isEnableImplicitTableGroup();
            return (supportAlterManuallyTg && isManuallyCreateTg) ||
                (supportAlterImplicitTg && enableImplicitTgOfCN && !isManuallyCreateTg);
        }
        return false;
    }

    public DDLRecord getDdlRecord() {
        return ddlRecord;
    }

    public void setDdlRecord(DDLRecord ddlRecord) {
        this.ddlRecord = ddlRecord;
    }

    public boolean isVisibleToPolardbX() {
        return visibleToPolardbX;
    }

    public void setVisibleToPolardbX(boolean visibleToPolardbX) {
        this.visibleToPolardbX = visibleToPolardbX;
        this.visible = visibleToMysql || visibleToPolardbX;
    }

    public boolean isVisibleToMysql() {
        return visibleToMysql;
    }

    public void setVisibleToMysql(boolean visibleToMysql) {
        this.visibleToMysql = visibleToMysql;
        this.visible = visibleToMysql || visibleToPolardbX;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!this.visible) {
            this.visibleToMysql = false;
            this.visibleToPolardbX = false;
        }
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public BinlogPosition getPosition() {
        return position;
    }

    public void setPosition(BinlogPosition position) {
        this.position = position;
    }

    public QueryEventBuilder getQueryEventBuilder() {
        return queryEventBuilder;
    }

    public void setQueryEventBuilder(QueryEventBuilder queryEventBuilder) {
        this.queryEventBuilder = queryEventBuilder;
    }

    public String getCommitKey() {
        return commitKey;
    }

    public void setCommitKey(String commitKey) {
        this.commitKey = commitKey;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Long getServerId() {
        DDLExtInfo ddlExtInfo = getDdlRecord().getExtInfo();
        if (ddlExtInfo != null && StringUtils.isNotBlank(ddlExtInfo.getServerId()) && NumberUtils.isCreatable(
            ddlExtInfo.getServerId())) {
            return NumberUtils.createLong(ddlExtInfo.getServerId());
        }
        return null;
    }

}
