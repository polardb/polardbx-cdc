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

import com.alibaba.fastjson.JSONObject;
import com.alibaba.otter.canal.common.utils.JsonUtils;
import com.alibaba.otter.canal.meta.CanalMetaManager;
import com.alibaba.otter.canal.meta.MemoryMetaManager;
import com.alibaba.otter.canal.meta.exception.CanalMetaManagerException;
import com.alibaba.otter.canal.protocol.ClientIdentity;
import com.alibaba.otter.canal.protocol.position.LogPosition;
import com.alibaba.otter.canal.protocol.position.Position;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.MigrateMap;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CanalReaderMetaManager extends MemoryMetaManager implements CanalMetaManager {
    private ScheduledExecutorService executor;
    private final Position nullCursor = new Position() {
    };
    private Set<ClientIdentity> updateCursorTasks;

    public void start() {
        super.start();

        tryCreateTable();
        executor = Executors.newScheduledThreadPool(1);
        destinations = MigrateMap.makeComputingMap(this::loadClientIdentity);

        cursors = MigrateMap.makeComputingMap(clientIdentity -> {
            Position position = loadCursor(clientIdentity.getDestination(), clientIdentity);
            if (position == null) {
                return nullCursor; // 返回一个空对象标识，避免出现异常
            } else {
                return position;
            }
        });

        updateCursorTasks = Collections.synchronizedSet(new HashSet<>());

        // 启动定时工作任务
        // 单位ms
        long period = 1000;
        executor.scheduleAtFixedRate(() -> {
                List<ClientIdentity> tasks = new ArrayList<>(updateCursorTasks);
                for (ClientIdentity clientIdentity : tasks) {
                    MDC.put("destination", String.valueOf(clientIdentity.getDestination()));
                    try {
                        updateCursorTasks.remove(clientIdentity);

                        // 定时将内存中的最新值刷到file中，多次变更只刷一次
                        if (log.isInfoEnabled()) {
                            LogPosition cursor = (LogPosition) getCursor(clientIdentity);
                            log.info("clientId:{} cursor:[{},{},{},{},{}] address[{}]", clientIdentity.getClientId(),
                                cursor.getPostion().getJournalName(),
                                cursor.getPostion().getPosition(), cursor.getPostion().getTimestamp(),
                                cursor.getPostion().getServerId(), cursor.getPostion().getGtid(),
                                cursor.getIdentity().getSourceAddress().toString());
                        }
                        flushDataToDB(clientIdentity.getDestination());
                    } catch (Throwable e) {
                        // ignore
                        log.error("period update" + clientIdentity.toString() + " curosr failed!", e);
                    }
                }
            },
            period,
            period,
            TimeUnit.MILLISECONDS);
    }

    public void stop() {
        flushDataToDB();// 刷新数据

        super.stop();
        executor.shutdownNow();
        destinations.clear();
        batches.clear();
    }

    @Override
    public void updateCursor(ClientIdentity clientIdentity, Position position) throws CanalMetaManagerException {
        updateCursorTasks.add(clientIdentity);// 添加到任务队列中进行触发
        super.updateCursor(clientIdentity, position);
    }

    @Override
    public Position getCursor(ClientIdentity clientIdentity) throws CanalMetaManagerException {
        Position position = super.getCursor(clientIdentity);
        if (position == nullCursor) {
            return null;
        } else {
            return position;
        }
    }

    // ============================ helper method ======================

    public static MetaInstanceData loadDataFromDB(String destination) {
        String metaInfo = queryDataFromDB(destination);
        if (StringUtils.isBlank(metaInfo)) {
            return null;
        }

        return JsonUtils.unmarshalFromString(metaInfo, MetaInstanceData.class);
    }

    private void flushDataToDB() {
        for (String destination : destinations.keySet()) {
            flushDataToDB(destination);
        }
    }

    private void flushDataToDB(String destination) {
        MetaInstanceData data = new MetaInstanceData();
        if (destinations.containsKey(destination)) {
            synchronized (destination.intern()) { // 基于destination控制一下并发更新
                data.setDestination(destination);

                List<MetaClientIdentityData> clientDatas = new ArrayList<>();
                List<ClientIdentity> clientIdentitys = destinations.get(destination);
                for (ClientIdentity clientIdentity : clientIdentitys) {
                    MetaClientIdentityData clientData = new MetaClientIdentityData();
                    clientData.setClientIdentity(clientIdentity);
                    Position position = cursors.get(clientIdentity);
                    if (position != null && position != nullCursor) {
                        clientData.setCursor((LogPosition) position);
                    }

                    clientDatas.add(clientData);
                }

                data.setClientDatas(clientDatas);
            }
            //fixed issue https://github.com/alibaba/canal/issues/4312
            //客户端数据为空时不覆盖文件内容 （适合单客户端）
            if (data.getClientDatas().isEmpty()) {
                return;
            }
            String json = JsonUtils.marshalToString(data);
            updateDataToDB(destination, json);
        }
    }

    private List<ClientIdentity> loadClientIdentity(String destination) {
        List<ClientIdentity> result = Lists.newArrayList();

        MetaInstanceData data = loadDataFromDB(destination);
        if (data == null) {
            log.info("load client identity {} for destination {}", JSONObject.toJSONString(result), destination);
            return result;
        }

        List<MetaClientIdentityData> clientDatas = data.getClientDatas();
        if (clientDatas == null) {
            log.info("load client identity {} for destination {}", JSONObject.toJSONString(result), destination);
            return result;
        }

        for (MetaClientIdentityData clientData : clientDatas) {
            if (clientData.getClientIdentity().getDestination().equals(destination)) {
                result.add(clientData.getClientIdentity());
            }
        }

        log.info("load client identity {} for destination {}", JSONObject.toJSONString(result), destination);

        return result;
    }

    private Position loadCursor(String destination, ClientIdentity clientIdentity) {
        MetaInstanceData data = loadDataFromDB(destination);
        if (data == null) {
            return null;
        }

        List<MetaClientIdentityData> clientDatas = data.getClientDatas();
        if (clientDatas == null) {
            return null;
        }

        for (MetaClientIdentityData clientData : clientDatas) {
            if (clientData.getClientIdentity() != null && clientData.getClientIdentity().equals(clientIdentity)) {
                return clientData.getCursor();
            }
        }

        return null;
    }

    @Setter
    public static class MetaClientIdentityData {

        @Getter
        private ClientIdentity clientIdentity;
        private LogPosition cursor;

        public MetaClientIdentityData() {

        }

        public MetaClientIdentityData(ClientIdentity clientIdentity, MemoryClientIdentityBatch batch,
                                      LogPosition cursor) {
            this.clientIdentity = clientIdentity;
            this.cursor = cursor;
        }

        public Position getCursor() {
            return cursor;
        }

    }

    @Getter
    public static class MetaInstanceData {

        private String destination;
        private List<MetaClientIdentityData> clientDatas;

        public MetaInstanceData() {

        }

        public MetaInstanceData(String destination, List<MetaClientIdentityData> clientDatas) {
            this.destination = destination;
            this.clientDatas = clientDatas;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public void setClientDatas(List<MetaClientIdentityData> clientDatas) {
            this.clientDatas = clientDatas;
        }

    }

    @SneakyThrows
    private static void tryCreateTable() {
        String sql = "create table if not exists t_cdc_canal_test("
            + " destination varchar(100) primary key, "
            + " meta_info text not null)";
        try (Connection connection = ConnectionManager.getInstance().getMetaDataSource().getConnection()) {
            JdbcUtil.executeUpdate(connection, sql);
        }
    }

    @SneakyThrows
    private static String queryDataFromDB(String destination) {
        String sql = String.format("select meta_info from t_cdc_canal_test where destination = '%s'", destination);
        try (Connection connection = ConnectionManager.getInstance().getMetaDataSource().getConnection()) {
            return JdbcUtil.executeQueryAndGetStringResult(sql, connection, 1);
        }
    }

    @SneakyThrows
    private static void updateDataToDB(String destination, String metaInfo) {
        String sql = String.format(
            "replace into t_cdc_canal_test(destination,meta_info)values('%s','%s')", destination, metaInfo);
        try (Connection connection = ConnectionManager.getInstance().getMetaDataSource().getConnection()) {
            JdbcUtil.executeUpdate(connection, sql);
        }
    }
}
