/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

public abstract class AbstractMysqlEventParser extends AbstractEventParser {

    protected static final long BINLOG_START_OFFEST = 4L;

    // 编码信息
    protected boolean filterQueryDcl = false;
    protected boolean filterQueryDml = false;
    protected boolean filterQueryDdl = false;
    protected boolean filterRows = false;
    protected boolean filterTableError = false;
    protected boolean useDruidDdlFilter = true;
    protected boolean rds = false;

    // ============================ setter / getter =========================

    public void setFilterQueryDcl(boolean filterQueryDcl) {
        this.filterQueryDcl = filterQueryDcl;
    }

    public void setFilterQueryDml(boolean filterQueryDml) {
        this.filterQueryDml = filterQueryDml;
    }

    public void setFilterQueryDdl(boolean filterQueryDdl) {
        this.filterQueryDdl = filterQueryDdl;
    }

    public void setFilterRows(boolean filterRows) {
        this.filterRows = filterRows;
    }

    public void setFilterTableError(boolean filterTableError) {
        this.filterTableError = filterTableError;
    }

    public boolean isUseDruidDdlFilter() {
        return useDruidDdlFilter;
    }

    public void setUseDruidDdlFilter(boolean useDruidDdlFilter) {
        this.useDruidDdlFilter = useDruidDdlFilter;
    }

    public boolean isRds() {
        return rds;
    }

    public void setRds(boolean rds) {
        this.rds = rds;
    }
}
