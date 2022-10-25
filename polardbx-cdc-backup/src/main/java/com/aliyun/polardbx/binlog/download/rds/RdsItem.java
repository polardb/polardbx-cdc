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
package com.aliyun.polardbx.binlog.download.rds;

import java.util.List;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:52
 * @since 3.2.6
 */
public class RdsItem {
    private List<BinlogFile> BinLogFile;

    public List<BinlogFile> getBinLogFile() {
        return BinLogFile;
    }

    public void setBinLogFile(List<BinlogFile> binLogFile) {
        BinLogFile = binLogFile;
    }
}
