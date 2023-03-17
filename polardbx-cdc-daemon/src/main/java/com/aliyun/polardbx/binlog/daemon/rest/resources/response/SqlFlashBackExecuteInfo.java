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
package com.aliyun.polardbx.binlog.daemon.rest.resources.response;

import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Created by ziyang.lb
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlFlashBackExecuteInfo {
    private Long fsmId;
    private String fsmState;
    private String fsmStatus;
    //OSS or LINDORM
    private String fileStorageType;
    private String filePathPrefix;
    private Long sqlCounter;
    private String downloadUrl;
    //单位：ms
    private long expireTime;
    private double progress;
    private List<ServiceDetail> serviceDetailList;
}
