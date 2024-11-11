/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta.domain;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@ToString
@EqualsAndHashCode
public class DDLExtInfo {
    private Long taskId;
    private Long taskSubSeq;
    private String createSql4PhyTable;
    private String serverId;
    private String sqlMode = null;
    private Boolean useOMC;
    private String groupName;

    /**
     * 在使用这个属性时需要注意,历史上出现过这样的情况 @承谨
     * CN进行序列化时使用的是fastjson，isGsi序列化后的的名字为gsi，但cdc的部分代码是使用Gson进行的反序列化
     * fastjson对isGsi和gsi是完全兼容的，但Gson只能识别isGsi，导致isGsi实际为true，但Gson反序列化之后的值为false
     * 导致记录到binlog_logic_meta_history中的值是不对的，好在基于目前没有地方基于binlog_logic_meta_history中记录的值进行逻辑处理
     * fastjson和gson的处理差异，参见：com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfoTest#testGsi()
     */
    private boolean isGsi = false;

    /**
     * cci标记
     */
    private boolean cci = false;

    /**
     * 历史原因，originalDdl，拼写成了orginalDdl @承谨
     * 另外，使用orginalDdl的版本，在对GSI进行打标处理时，也存在诸多缺陷，参见：https://aone.alibaba-inc.com/v2/project/860366/bug/51253282
     * 将错就错，对于isGsi为true的场景，根据原始sql是保存在了orginalDdl，还是originalDdl，来做不同的处理，@see RebuildEventLogFilter
     */
    private String originalDdl;
    private String orginalDdl;
    private int ddlScope;
    private Boolean manuallyCreatedTableGroup;
    private boolean enableImplicitTableGroup;

    /**
     * 外键DDL标记
     */
    @Getter
    private Boolean foreignKeysDdl;

    @Getter
    private String flags2;

    private Map<String, Object> polarxVariables;

    public static DDLExtInfo parseExtInfo(String str) {
        DDLExtInfo extInfo = null;
        if (StringUtils.isNotEmpty(str)) {
            extInfo = JSONObject.parseObject(str, DDLExtInfo.class);
            if (extInfo != null && StringUtils.isNotBlank(extInfo.getCreateSql4PhyTable())) {
                extInfo.setCreateSql4PhyTable(StringUtils.lowerCase(extInfo.getCreateSql4PhyTable()));
            }
        }
        return extInfo;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getTaskSubSeq() {
        return taskSubSeq;
    }

    public void setTaskSubSeq(Long taskSubSeq) {
        this.taskSubSeq = taskSubSeq;
    }

    public String getCreateSql4PhyTable() {
        return createSql4PhyTable;
    }

    public void setCreateSql4PhyTable(String createSql4PhyTable) {
        this.createSql4PhyTable = createSql4PhyTable;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getSqlMode() {
        return sqlMode;
    }

    public void setSqlMode(String sqlMode) {
        this.sqlMode = sqlMode;
    }

    public Boolean getUseOMC() {
        return useOMC;
    }

    public void setUseOMC(Boolean useOMC) {
        this.useOMC = useOMC;
    }

    @Deprecated
    public String getOriginalDdl() {
        return originalDdl;
    }

    @Deprecated
    public void setOriginalDdl(String originalDdl) {
        this.originalDdl = originalDdl;
    }

    @Deprecated
    public String getOrginalDdl() {
        return orginalDdl;
    }

    @Deprecated
    public void setOrginalDdl(String orginalDdl) {
        this.orginalDdl = orginalDdl;
    }

    public boolean isGsi() {
        return isGsi;
    }

    public void setGsi(boolean gsi) {
        isGsi = gsi;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getDdlScope() {
        return ddlScope;
    }

    public void setDdlScope(int ddlScope) {
        this.ddlScope = ddlScope;
    }

    public boolean isOldVersionOriginalSql() {
        return StringUtils.isNotBlank(orginalDdl);
    }

    public void setFlags2(String flags2) {
        this.flags2 = flags2;
    }

    public String getActualOriginalSql() {
        if (StringUtils.isNotBlank(orginalDdl)) {
            return orginalDdl;
        } else {
            return originalDdl;
        }
    }

    public void setForeignKeysDdl(Boolean foreignKeysDdl) {
        this.foreignKeysDdl = foreignKeysDdl;
    }

    public Boolean getManuallyCreatedTableGroup() {
        return manuallyCreatedTableGroup;
    }

    public void setManuallyCreatedTableGroup(Boolean manuallyCreatedTableGroup) {
        this.manuallyCreatedTableGroup = manuallyCreatedTableGroup;
    }

    public boolean isEnableImplicitTableGroup() {
        return enableImplicitTableGroup;
    }

    public void setEnableImplicitTableGroup(boolean enableImplicitTableGroup) {
        this.enableImplicitTableGroup = enableImplicitTableGroup;
    }

    public boolean isCci() {
        return cci;
    }

    public void setCci(boolean cci) {
        this.cci = cci;
    }

    public void resetOriginalSql(String newSql) {
        if (StringUtils.isBlank(orginalDdl) && StringUtils.isBlank(originalDdl)) {
            throw new PolardbxException("can`t reset original sql , because both orginalDdl and originalDdl is null");
        }
        if (StringUtils.isNotBlank(orginalDdl) && StringUtils.isNotBlank(originalDdl)) {
            throw new PolardbxException(
                "can`t reset original sql , because both orginalDdl and originalDdl is not empty");
        }

        if (StringUtils.isNotBlank(orginalDdl)) {
            orginalDdl = newSql;
        } else {
            originalDdl = newSql;
        }
    }

    public Map<String, Object> getPolarxVariables() {
        return polarxVariables;
    }

    public void setPolarxVariables(Map<String, Object> polarxVariables) {
        this.polarxVariables = polarxVariables;
    }
}
