/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.model;

import com.alibaba.fastjson.JSON;

import java.net.InetSocketAddress;

/**
 * 数据库认证信息
 *
 * @author jianghang 2012-7-11 上午11:22:19
 * @version 1.0.0
 */
public class AuthenticationInfo {

    private InetSocketAddress address; // 主库信息
    private String username; // 帐号
    private String password; // 密码
    private String charset = "UTF-8";
    private String storageMasterInstId;
    private String storageInstId;
    private String uid;
    private String bid;

    public AuthenticationInfo() {
        super();
    }

    public AuthenticationInfo(InetSocketAddress address, String username, String password) {
        this.address = address;
        this.username = username;
        this.password = password;
    }

    public AuthenticationInfo(InetSocketAddress address, String username, String password, String charset) {
        this(address, username, password);
        this.charset = charset;
    }

    public String getStorageMasterInstId() {
        return storageMasterInstId;
    }

    public void setStorageMasterInstId(String storageMasterInstId) {
        this.storageMasterInstId = storageMasterInstId;
    }

    public String getStorageInstId() {
        return storageInstId;
    }

    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getBid() {
        return bid;
    }

    public void setBid(String bid) {
        this.bid = bid;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AuthenticationInfo)) {
            return false;
        }
        AuthenticationInfo other = (AuthenticationInfo) obj;
        if (address == null) {
            if (other.address != null) {
                return false;
            }
        } else if (!address.equals(other.address)) {
            return false;
        }
        if (password == null) {
            if (other.password != null) {
                return false;
            }
        } else if (!password.equals(other.password)) {
            return false;
        }
        if (username == null) {
            if (other.username != null) {
                return false;
            }
        } else if (!username.equals(other.username)) {
            return false;
        }
        return true;
    }

}
