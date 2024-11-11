/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instId;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.isVip;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.storageInstId;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.storageMasterInstId;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * @author yudong
 * @since 2023/5/10 16:12
 **/
@Service
@Slf4j
public class StorageInfoService {

    @Resource
    private StorageInfoMapper mapper;
    private Function<String, String> masterUrlProvider;
    private Function<StorageInfo, Boolean> dnHealthChecker;

    public StorageInfoService() {
        this.masterUrlProvider = this::getMasterUrlByShowStorage;
        this.dnHealthChecker = this::isAvailable;
    }

    public StorageInfo getNormalStorageInfo(String sid) {
        StorageInfo result = null;
        List<StorageInfo> storageInfos =
            new ArrayList<>(mapper.select(s -> s.where(storageInstId, isEqualTo(sid)).and(instKind, isEqualTo(0))));
        if (storageInfos.isEmpty()) {
            return null;
        }

        String masterUrl = masterUrlProvider.apply(sid);
        log.info("try get dn leader url, storage inst id:{}, url:{}", sid, masterUrl);
        if (masterUrl != null) {
            String[] ipAndPort = masterUrl.split(":");
            String ip = ipAndPort[0];
            int port = Integer.parseInt(ipAndPort[1]);
            for (StorageInfo info : storageInfos) {
                if (info.getIp().equals(ip) && info.getPort() == port && dnHealthChecker.apply(info)) {
                    return info;
                }
            }
        }

        for (StorageInfo storageInfo : storageInfos) {
            if (dnHealthChecker.apply(storageInfo)) {
                result = storageInfo;
                break;
            }
        }
        return result;
    }

    public StorageInfo getLocalStorageInfo(String polarxInstId, String masterInstId) {
        StorageInfo result = null;
        Optional<StorageInfo> vipStorage = mapper.selectOne(
            s -> s.where(instId, isEqualTo(polarxInstId)).and(storageMasterInstId, isEqualTo(masterInstId))
                .and(isVip, isNotEqualTo(1)));
        if (vipStorage.isPresent() && dnHealthChecker.apply(vipStorage.get())) {
            result = vipStorage.get();
        } else {
            List<StorageInfo> storageInfos = new ArrayList<>(mapper.select(
                s -> s.where(instId, isEqualTo(polarxInstId)).and(storageMasterInstId, isEqualTo(masterInstId))));
            for (StorageInfo storageInfo : storageInfos) {
                if (dnHealthChecker.apply(storageInfo)) {
                    result = storageInfo;
                    break;
                }
            }
        }
        return result;
    }

    private String getMasterUrlByShowStorage(String storageInstId) {
        if (StringUtils.isEmpty(storageInstId)) {
            throw new IllegalArgumentException("storage inst id is empty");
        }

        String result = null;
        JdbcTemplate cnTemplate = getObject("polarxJdbcTemplate");
        List<Map<String, Object>> maps = cnTemplate.queryForList("SHOW STORAGE");
        for (Map<String, Object> map : maps) {
            String sid = (String) map.get("STORAGE_INST_ID");
            if (storageInstId.equals(sid)) {
                result = (String) map.get("LEADER_NODE");
            }
        }
        return result;
    }

    public boolean isAvailable(StorageInfo storageInfo) {
        String url = String.format("jdbc:mysql://%s:%s/mysql?useSSL=false", storageInfo.getIp(), storageInfo.getPort());
        String userName = storageInfo.getUser();
        String password = PasswdUtil.decryptBase64(storageInfo.getPasswdEnc());
        try (Connection conn = DriverManager.getConnection(url, userName, password)) {
            return true;
        } catch (Exception e) {
            log.info("dn is not available: {}", url, e);
            return false;
        }
    }

    public Function<String, String> getMasterUrlProvider() {
        return masterUrlProvider;
    }

    public void setMasterUrlProvider(Function<String, String> masterUrlProvider) {
        this.masterUrlProvider = masterUrlProvider;
    }

    public Function<StorageInfo, Boolean> getDnHealthChecker() {
        return dnHealthChecker;
    }

    public void setDnHealthChecker(
        Function<StorageInfo, Boolean> dnHealthChecker) {
        this.dnHealthChecker = dnHealthChecker;
    }
}
