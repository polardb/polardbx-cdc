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
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryMapper;
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Slf4j
public class TimelineEnvConfig {

    private final ConcurrentHashMap<String, String> configMap = new ConcurrentHashMap<>();

    public String getValue(String key) {
        String value = configMap.get(key);
        if (StringUtils.isEmpty(value)) {
            return DynamicApplicationConfig.getValue(key);
        }
        return value;
    }

    public String getString(String key) {
        return getValue(key);
    }

    public String getString(String key, String defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    public Integer getInt(String key) {
        return Integer.parseInt(getValue(key));
    }

    public Integer getInt(String key, int defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    public Long getLong(String key) {
        return Long.parseLong(getValue(key));
    }

    public Long getLong(String key, long defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Long.parseLong(value);
    }

    public Boolean getBoolean(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    public Boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public Double getDouble(String key) {
        return Double.parseDouble(getValue(key));
    }

    public Double getDouble(String key, double defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Double.parseDouble(value);
    }

    public void refreshConfigByTso(String tso) {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        String content = metaTemplate
            .query("select change_env_content from binlog_env_config_history where tso = ? ",
                new ArgumentPreparedStatementSetter(new Object[] {tso}),
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return resultSet.getString(1);
                });

        if (StringUtils.isNotBlank(content)) {
            JSONObject jsonObject = JSON.parseObject(content);
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                configMap.put(entry.getKey(), (String) entry.getValue());
            }
        } else {
            throw new PolardbxException("time line env config is not exists for tso " + tso);
        }
    }

    public void initConfigByTso(String tso) {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> contentList = metaTemplate
            .query(
                "select change_env_content from binlog_env_config_history where tso <= ? order by tso asc",
                new ArgumentPreparedStatementSetter(new Object[] {tso}),
                resultSet -> {
                    List<String> resultList = Lists.newArrayList();
                    while (resultSet.next()) {
                        resultList.add(resultSet.getString(1));
                    }
                    return resultList;
                });

        if (contentList != null) {
            contentList.forEach(c -> {
                JSONObject jsonObject = JSON.parseObject(c);
                for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                    configMap.put(entry.getKey(), (String) entry.getValue());
                }
            });
        }
    }

    public void tryRecordEnvConfigHistory(final String tso,
                                          EnvConfigChangeInfo envConfigChangeInfo) {
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        final BinlogEnvConfigHistoryMapper configHistoryMapper =
            SpringContextHolder.getObject(BinlogEnvConfigHistoryMapper.class);
        transactionTemplate.execute((o) -> {
            // 幂等判断
            // tso记录到binlog文件和记录到BinlogEnvConfigHistory表是非原子操作，需要做幂等判断
            // instructionId也是不能重复的，正常情况下tso和instructionId是一对一的关系，但可能出现bug
            // 比如：https://yuque.antfin-inc.com/jingwei3/knddog/uxpbzq，所以此处查询要更严谨一些，where条件也要包含instructionId
            List<BinlogEnvConfigHistory> configHistoryList = configHistoryMapper.select(
                s -> s.where(BinlogEnvConfigHistoryDynamicSqlSupport.tso, isEqualTo(tso))
                    .and(BinlogEnvConfigHistoryDynamicSqlSupport.instructionId,
                        isEqualTo(envConfigChangeInfo.getInstructionId())));

            try {
                if (configHistoryList.isEmpty()) {
                    BinlogEnvConfigHistory binlogEnvConfigHistory = new BinlogEnvConfigHistory();
                    binlogEnvConfigHistory.setTso(tso);
                    binlogEnvConfigHistory.setInstructionId(envConfigChangeInfo.getInstructionId());
                    binlogEnvConfigHistory.setChangeEnvContent(envConfigChangeInfo.getContent());
                    configHistoryMapper.insertSelective(binlogEnvConfigHistory);
                    log.info("record meta env config change history : " + JSONObject.toJSONString(envConfigChangeInfo));
                } else {
                    log.info("env config change  history with tso {} or instruction id {} is already exist, ignored.",
                        tso, envConfigChangeInfo.getInstructionId());
                }
            } catch (DuplicateKeyException ignore) {
            }
            return null;
        });
    }

}
