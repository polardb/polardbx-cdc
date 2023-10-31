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
package com.aliyun.polardbx.binlog.format.utils.generator;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

public class CdcGenerateUtil extends BinlogGenerateUtil {
    public CdcGenerateUtil(boolean mysql8) {
        super(mysql8);
    }

    public class CdcStart extends Transaction {

        public CdcStart() {
            super("__cdc___000000", "GROUP_XXX_1", true);
            defineTable(schemaName, "__cdc_instruction___q4oc", "bigint(20)", "varchar(50)", "mediumtext", "timestamp",
                "varchar(50)");
            addTableData("__cdc_instruction___q4oc", "600002", "CdcStart", "{}",
                DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"),
                CommonUtils.buildStartCmd());
        }

    }

    public Heartbeat newHeartbeat() {
        return new Heartbeat();
    }

    public CdcStart newCdcStart() {
        return new CdcStart();
    }

    public class Heartbeat extends Transaction {

        public Heartbeat() {
            super("__cdc___000000", "GROUP_XXX_1", true);
            defineTable(schemaName, "__cdc_heartbeat___89vr", "bigint(20)", "varchar(10)", "datetime(3)");
            addTableData("__cdc_heartbeat___89vr", "600002", "heartbeat",
                DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"));
        }

    }
}
