/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class UserPriv {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.494+08:00",
        comments = "Source field: user_priv.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.user_name")
    private String userName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.host")
    private String host;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.password")
    private String password;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.select_priv")
    private Boolean selectPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.insert_priv")
    private Boolean insertPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.update_priv")
    private Boolean updatePriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.delete_priv")
    private Boolean deletePriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.create_priv")
    private Boolean createPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.drop_priv")
    private Boolean dropPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.grant_priv")
    private Boolean grantPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.index_priv")
    private Boolean indexPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.alter_priv")
    private Boolean alterPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.show_view_priv")
    private Integer showViewPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_view_priv")
    private Integer createViewPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_user_priv")
    private Integer createUserPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.meta_db_priv")
    private Integer metaDbPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.account_type")
    private Boolean accountType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.show_audit_log_priv")
    private Integer showAuditLogPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.49+08:00",
        comments = "Source Table: user_priv")
    public UserPriv(Long id, Date gmtCreated, Date gmtModified, String userName, String host, String password,
                    Boolean selectPriv, Boolean insertPriv, Boolean updatePriv, Boolean deletePriv, Boolean createPriv,
                    Boolean dropPriv, Boolean grantPriv, Boolean indexPriv, Boolean alterPriv, Integer showViewPriv,
                    Integer createViewPriv, Integer createUserPriv, Integer metaDbPriv, Boolean accountType,
                    Integer showAuditLogPriv) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.userName = userName;
        this.host = host;
        this.password = password;
        this.selectPriv = selectPriv;
        this.insertPriv = insertPriv;
        this.updatePriv = updatePriv;
        this.deletePriv = deletePriv;
        this.createPriv = createPriv;
        this.dropPriv = dropPriv;
        this.grantPriv = grantPriv;
        this.indexPriv = indexPriv;
        this.alterPriv = alterPriv;
        this.showViewPriv = showViewPriv;
        this.createViewPriv = createViewPriv;
        this.createUserPriv = createUserPriv;
        this.metaDbPriv = metaDbPriv;
        this.accountType = accountType;
        this.showAuditLogPriv = showAuditLogPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.493+08:00",
        comments = "Source Table: user_priv")
    public UserPriv() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.495+08:00",
        comments = "Source field: user_priv.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.user_name")
    public String getUserName() {
        return userName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.user_name")
    public void setUserName(String userName) {
        this.userName = userName == null ? null : userName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.host")
    public String getHost() {
        return host;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.host")
    public void setHost(String host) {
        this.host = host == null ? null : host.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.password")
    public String getPassword() {
        return password;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.password")
    public void setPassword(String password) {
        this.password = password == null ? null : password.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.select_priv")
    public Boolean getSelectPriv() {
        return selectPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.select_priv")
    public void setSelectPriv(Boolean selectPriv) {
        this.selectPriv = selectPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.insert_priv")
    public Boolean getInsertPriv() {
        return insertPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.496+08:00",
        comments = "Source field: user_priv.insert_priv")
    public void setInsertPriv(Boolean insertPriv) {
        this.insertPriv = insertPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.update_priv")
    public Boolean getUpdatePriv() {
        return updatePriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.update_priv")
    public void setUpdatePriv(Boolean updatePriv) {
        this.updatePriv = updatePriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.delete_priv")
    public Boolean getDeletePriv() {
        return deletePriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.delete_priv")
    public void setDeletePriv(Boolean deletePriv) {
        this.deletePriv = deletePriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.create_priv")
    public Boolean getCreatePriv() {
        return createPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.create_priv")
    public void setCreatePriv(Boolean createPriv) {
        this.createPriv = createPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.drop_priv")
    public Boolean getDropPriv() {
        return dropPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.drop_priv")
    public void setDropPriv(Boolean dropPriv) {
        this.dropPriv = dropPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.grant_priv")
    public Boolean getGrantPriv() {
        return grantPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.497+08:00",
        comments = "Source field: user_priv.grant_priv")
    public void setGrantPriv(Boolean grantPriv) {
        this.grantPriv = grantPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.index_priv")
    public Boolean getIndexPriv() {
        return indexPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.index_priv")
    public void setIndexPriv(Boolean indexPriv) {
        this.indexPriv = indexPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.alter_priv")
    public Boolean getAlterPriv() {
        return alterPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.alter_priv")
    public void setAlterPriv(Boolean alterPriv) {
        this.alterPriv = alterPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.show_view_priv")
    public Integer getShowViewPriv() {
        return showViewPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.show_view_priv")
    public void setShowViewPriv(Integer showViewPriv) {
        this.showViewPriv = showViewPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_view_priv")
    public Integer getCreateViewPriv() {
        return createViewPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_view_priv")
    public void setCreateViewPriv(Integer createViewPriv) {
        this.createViewPriv = createViewPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_user_priv")
    public Integer getCreateUserPriv() {
        return createUserPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.create_user_priv")
    public void setCreateUserPriv(Integer createUserPriv) {
        this.createUserPriv = createUserPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.meta_db_priv")
    public Integer getMetaDbPriv() {
        return metaDbPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.498+08:00",
        comments = "Source field: user_priv.meta_db_priv")
    public void setMetaDbPriv(Integer metaDbPriv) {
        this.metaDbPriv = metaDbPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.account_type")
    public Boolean getAccountType() {
        return accountType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.account_type")
    public void setAccountType(Boolean accountType) {
        this.accountType = accountType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.show_audit_log_priv")
    public Integer getShowAuditLogPriv() {
        return showAuditLogPriv;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.499+08:00",
        comments = "Source field: user_priv.show_audit_log_priv")
    public void setShowAuditLogPriv(Integer showAuditLogPriv) {
        this.showAuditLogPriv = showAuditLogPriv;
    }
}