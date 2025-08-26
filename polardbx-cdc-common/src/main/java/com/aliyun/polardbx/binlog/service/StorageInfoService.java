/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.alibaba.druid.util.JdbcUtils;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_CHECK_DN_LEADER_BY_SHOW_STORAGE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instId;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.isVip;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
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
    private Function<StorageInfo, Boolean> dnLeaderChecker;
    private Function<List<StorageInfo>, List<StorageInfo>> followerStorageByClusterLocal;

    public StorageInfoService() {
        this.masterUrlProvider = this::getMasterUrlByShowStorage;
        this.dnHealthChecker = this::isAvailable;
        this.dnLeaderChecker = this::checkLeaderByDN;
        this.followerStorageByClusterLocal = this::getFollowerStorageByClusterLocal;
    }

    public StorageInfo getNormalStorageInfo(String sid) {
        StorageInfo result = null;
        List<StorageInfo> storageInfos = new ArrayList<>(mapper.select(
            s -> s.where(storageMasterInstId, isEqualTo(sid))
                .and(instKind, isEqualTo(0))
                .and(status, isNotEqualTo(2))));
        if (storageInfos.isEmpty()) {
            return null;
        }

        if (getBoolean(TOPOLOGY_CHECK_DN_LEADER_BY_SHOW_STORAGE)) {
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
        }

        for (StorageInfo info : storageInfos) {
            if (dnLeaderChecker.apply(info)) {
                result = info;
                log.info("find leader for {} , ip:{} , port:{}", info.getStorageInstId(), info.getIp(),
                    info.getPort());
                break;
            }
        }
        return result;
    }

    public StorageInfo getLocalStorageInfo(String polarxInstId, String masterInstId) {
        StorageInfo result = null;
        Optional<StorageInfo> vipStorage = mapper.selectOne(
            s -> s.where(instId, isEqualTo(polarxInstId))
                .and(storageMasterInstId, isEqualTo(masterInstId))
                .and(isVip, isEqualTo(1))
                .and(status, isNotEqualTo(2)));
        if (vipStorage.isPresent() && dnHealthChecker.apply(vipStorage.get())) {
            result = vipStorage.get();
        } else {
            List<StorageInfo> storageInfos = new ArrayList<>(mapper.select(
                s -> s.where(instId, isEqualTo(polarxInstId))
                    .and(storageMasterInstId, isEqualTo(masterInstId))
                    .and(status, isNotEqualTo(2))));
            for (StorageInfo storageInfo : storageInfos) {
                if (dnHealthChecker.apply(storageInfo)) {
                    result = storageInfo;
                    break;
                }
            }
        }
        return result;
    }

    public List<StorageInfo> getFollowerStorageInfo(String sid, Function<StorageInfo, Boolean> nodeFilter) {
        List<StorageInfo> storageInfos = new ArrayList<>(mapper.select(
            s -> s.where(storageMasterInstId, isEqualTo(sid))
                .and(instKind, isEqualTo(0))
                .and(status, isNotEqualTo(2))
                .and(isVip, isNotEqualTo(1))));
        if (storageInfos.isEmpty()) {
            return null;
        }

        List<StorageInfo> maybeFollowerList = new ArrayList<>();
        for (StorageInfo info : storageInfos) {
            if (nodeFilter == null || nodeFilter.apply(info)) {
                maybeFollowerList.add(info);
            }
        }

        return new ArrayList<>(followerStorageByClusterLocal.apply(maybeFollowerList));
    }

    public List<StorageInfo> getFollowerStorageByClusterLocal(List<StorageInfo> maybeFollowerList) {
        List<StorageInfo> result = new ArrayList<>();
        String instId = "";
        for (StorageInfo info : maybeFollowerList) {
            instId = info.getStorageInstId();
            try (Connection conn = getConnection(info)) {
                List<Map<String, Object>> resultMap =
                    JdbcUtils.executeQuery(conn, "select ROLE from information_schema.ALISQL_CLUSTER_LOCAL",
                        new ArrayList<>());
                if (!resultMap.isEmpty() && StringUtils.equalsIgnoreCase("follower",
                    (String) resultMap.get(0).get("ROLE"))) {
                    result.add(info);
                    log.info("find follower for {} , ip:{} , port:{}", info.getStorageInstId(), info.getIp(),
                        info.getPort());
                }
            } catch (Exception e) {
                log.warn("dn is not available: maybe logger inst {} , ip {}, errorMsg {}", info.getStorageInstId(),
                    info.getIp(), e.getMessage());
            }
        }
        if (result.isEmpty()) {
            log.warn("follower node list is empty for {}", instId);
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

    private Connection getConnection(StorageInfo storageInfo) throws SQLException {
        String url = String.format(
            "jdbc:mysql://%s:%s/mysql?useSSL=false&connectTimeout=5000&socketTimeout=5000&readTimeout=5000",
            storageInfo.getIp(), storageInfo.getPort());
        String userName = storageInfo.getUser();
        String password = PasswdUtil.decryptBase64(storageInfo.getPasswdEnc());
        return DriverManager.getConnection(url, userName, password);
    }

    public boolean isAvailable(StorageInfo storageInfo) {
        try (Connection conn = getConnection(storageInfo)) {
            return true;
        } catch (Exception e) {
            log.info("dn is not available: {}", storageInfo.getIp(), e);
            return false;
        }
    }

    public boolean checkLeaderByDN(StorageInfo storageInfo) {
        try (Connection conn = getConnection(storageInfo)) {
            return SQLUtils.isLeaderBySqlQuery(conn);
        } catch (Exception e) {
            log.info("check leader error, {}:{}:{}",
                storageInfo.getStorageInstId(), storageInfo.getIp(), storageInfo.getPort(), e);
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

    public void setDnHealthChecker(Function<StorageInfo, Boolean> dnHealthChecker) {
        this.dnHealthChecker = dnHealthChecker;
    }

    public Function<StorageInfo, Boolean> getDnLeaderChecker() {
        return dnLeaderChecker;
    }

    public void setDnLeaderChecker(
        Function<StorageInfo, Boolean> dnLeaderChecker) {
        this.dnLeaderChecker = dnLeaderChecker;
    }

    public void setFollowerStorageByClusterLocal(
        Function<List<StorageInfo>, List<StorageInfo>> followerStorageByClusterLocal) {
        this.followerStorageByClusterLocal = followerStorageByClusterLocal;
    }
}
