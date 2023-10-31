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

import static com.aliyun.polardbx.binlog.ConfigKeys.ASSIGNED_DN_IP;

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

    public static DnHost buildHostForExtractor(String storageInstId) {
        DnHost result;
        if (CommonUtils.isGlobalBinlogSlave()) {
            Boolean sameRegion = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG);
            result = sameRegion ? DnHost.getLocalDnHost(storageInstId) : DnHost.getNormalDnHost(storageInstId);
        } else {
            result = DnHost.getNormalDnHost(storageInstId);
        }
        return result;
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
