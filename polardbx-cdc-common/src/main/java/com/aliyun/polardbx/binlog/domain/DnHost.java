/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.service.StorageInfoService;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.ASSIGNED_DN_IP;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

/**
 * master正常的找流程：
 * 通过storage_inst_id作为筛选条件，找有没有is_vip=1的，如果有则返回；如果没有，则随机挑选一个。
 * <p>
 * slave就近访问的流程：
 * 通过inst_id和storage_master_inst_id作为筛选条件，找is_vip=0的，随机挑选一个
 * <p>
 * slave降级：
 * 通过storage_inst_id作为筛选条件，找有没有is_vip=1的，如果有则返回；如果没有，则随机挑选一个。
 *
 * @author chengjin.lyf, yudong
 * @since 1.0.25
 */
@Getter
@AllArgsConstructor
@ToString
@Slf4j
public class DnHost {
    private final String ip;
    private final Integer port;
    private final String userName;
    private final String password;
    private final String charset;
    private final String storageInstId;

    public static List<DnHost> buildHostForExtractor(String storageInstId) {
        List<DnHost> resultList = new ArrayList<>();
        String polarxInstId = DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID);

        if (CommonUtils.isGlobalBinlogSlave()) {
            Boolean sameRegion = getBoolean(ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG);
            if (sameRegion) {
                DnHost localDnHost = getLocalDnHost(storageInstId);
                resultList.add(localDnHost);
            } else {
                DnHost result = getNormalDnHost(storageInstId);
                resultList.add(result);
                List<DnHost> followerResult = getFollowerDnHost(storageInstId,
                    storageInfo -> !StringUtils.equalsIgnoreCase(storageInfo.getInstId(), polarxInstId));
                if (followerResult != null) {
                    resultList.addAll(followerResult);
                }
            }
        } else {
            DnHost result = getNormalDnHost(storageInstId);
            resultList.add(result);
            List<DnHost> followerResult = getFollowerDnHost(storageInstId,
                storageInfo -> StringUtils.equalsIgnoreCase(storageInfo.getInstId(), polarxInstId));
            if (followerResult != null) {
                resultList.addAll(followerResult);
            }
        }
        return resultList;
    }

    public static DnHost getNormalDnHost(String storageInstId) {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        StorageInfo storageInfo = service.getNormalStorageInfo(storageInstId);
        if (storageInfo == null) {
            log.error("failed to get normal dn host, storage inst id:{}", storageInstId);
            throw new PolardbxException("cannot get storage info from metaDB!");
        }
        return fromStorageInfo(storageInfo);
    }

    static List<DnHost> getFollowerDnHost(String storageInstId, Function<StorageInfo, Boolean> nodeFilter) {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        List<StorageInfo> storageInfoList = service.getFollowerStorageInfo(storageInstId, nodeFilter);
        if (storageInfoList == null || storageInfoList.isEmpty()) {
            log.error("failed to get follower dn host, storage inst id:{}", storageInstId);
            return null;
        }
        return storageInfoList.stream().map(DnHost::fromStorageInfo).collect(Collectors.toList());
    }

    public static DnHost getLocalDnHost(String storageMasterInstId) {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        String polarxInstId = DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID);
        StorageInfo storageInfo = service.getLocalStorageInfo(polarxInstId, storageMasterInstId);
        if (storageInfo == null) {
            log.error("failed to get local dn host, storage master inst id:{}", storageMasterInstId);
            throw new PolardbxException("cannot get storage info from metaDB!");
        }
        return fromStorageInfo(storageInfo);
    }

    private static DnHost fromStorageInfo(StorageInfo info) {
        String ip;
        if (StringUtils.isNotBlank(DynamicApplicationConfig.getString(ASSIGNED_DN_IP))) {
            ip = DynamicApplicationConfig.getString(ASSIGNED_DN_IP);
        } else {
            ip = info.getIp();
        }
        String passwordEnc = info.getPasswdEnc();
        String password = PasswdUtil.decryptBase64(passwordEnc);

        return new DnHost(ip, info.getPort(), info.getUser(), password, "utf8", info.getStorageInstId());
    }
}
