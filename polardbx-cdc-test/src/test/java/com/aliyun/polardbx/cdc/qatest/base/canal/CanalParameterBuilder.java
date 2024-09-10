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
package com.aliyun.polardbx.cdc.qatest.base.canal;

import com.alibaba.otter.canal.instance.manager.model.CanalParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConfigConstant;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.PasswdUtil;
import com.google.common.collect.Lists;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.META_ADDRESS;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.META_PASSWORD;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.META_PORT;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.META_USER;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_ADDRESS;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_PORT;
import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.DEFAULT_CONN_PROPS;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.configProp;

public class CanalParameterBuilder {

    public static CanalParameter buildCanalParameter() {
        CanalParameter parameter = new CanalParameter();

        // polardbx address
        ArrayList<CanalParameter.DataSourcing> dataSourcing = new ArrayList<>();
        dataSourcing.add(new CanalParameter.DataSourcing(
            CanalParameter.SourcingType.MYSQL,
            new InetSocketAddress(configProp.getProperty(POLARDBX_ADDRESS),
                Integer.parseInt(configProp.getProperty(POLARDBX_PORT)))));
        parameter.setGroupDbAddresses(Lists.<List<CanalParameter.DataSourcing>>newArrayList(
            Lists.newArrayList(dataSourcing)
        ));
        parameter.setDbUsername(configProp.getProperty(ConfigConstant.POLARDBX_USER));
        parameter.setDbPassword(configProp.getProperty(ConfigConstant.POLARDBX_PASSWORD));
        parameter.setDefaultDatabaseName("polardbx");

        // tsdb address
        parameter.setTsdbEnable(true);
        parameter.setTsdbJdbcUrl(JdbcUtil.createUrl(
            configProp.getProperty(META_ADDRESS),
            Integer.parseInt(configProp.getProperty(META_PORT)),
            "polardbx_meta_db",
            DEFAULT_CONN_PROPS));
        parameter.setTsdbJdbcUserName(configProp.getProperty(META_USER));
        parameter.setTsdbJdbcPassword(PasswdUtil.decrypt(configProp.getProperty(META_PASSWORD)));

        parameter.setMemoryStorageRawEntry(false);
        parameter.setSlaveId(9999L);
        parameter.setIndexMode(CanalParameter.IndexMode.META);

        return parameter;
    }
}
