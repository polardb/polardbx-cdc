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
package com.aliyun.polardbx.binlog.canal.core.model;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServerCharactorSet {

    private String characterSetClient = "utf8";
    private String characterSetConnection = "utf8";
    private String characterSetDatabase = "utf8";
    private String characterSetServer = "utf8";

    public String getCharacterSetClient() {
        return characterSetClient;
    }

    public void setCharacterSetClient(String characterSetClient) {
        this.characterSetClient = SQLUtils.normalize(characterSetClient);
    }

    public String getCharacterSetConnection() {
        return characterSetConnection;
    }

    public void setCharacterSetConnection(String characterSetConnection) {
        this.characterSetConnection = SQLUtils.normalize(characterSetConnection);
    }

    public String getCharacterSetDatabase() {
        return characterSetDatabase;
    }

    public void setCharacterSetDatabase(String characterSetDatabase) {
        this.characterSetDatabase = SQLUtils.normalize(characterSetDatabase);
    }

    public String getCharacterSetServer() {
        return characterSetServer;
    }

    public void setCharacterSetServer(String characterSetServer) {
        this.characterSetServer = SQLUtils.normalize(characterSetServer);
    }

    public static ServerCharactorSet loadCharactorSetFromCN() {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        List<Pair<String, String>> list = jdbcTemplate.query("show variables like '%character%'",
            (rs, rowNum) -> Pair.of(rs.getString(1), rs.getString(2)));
        ServerCharactorSet set = new ServerCharactorSet();
        list.forEach(pair -> {
            String variableName = pair.getLeft();
            String charset = pair.getRight();
            ServerCharactorSet.commonSet(set, variableName, charset);
        });
        return set;
    }

    public static void commonSet(ServerCharactorSet set, String variableName, String charset) {
        if ("utf8mb3".equalsIgnoreCase(charset)) {
            charset = "utf8";
        }

        if ("character_set_client".equalsIgnoreCase(variableName)) {
            set.setCharacterSetClient(charset);
        } else if ("character_set_connection".equalsIgnoreCase(variableName)) {
            set.setCharacterSetConnection(charset);
        } else if ("character_set_database".equalsIgnoreCase(variableName)) {
            set.setCharacterSetDatabase(charset);
        } else if ("character_set_server".equalsIgnoreCase(variableName)) {
            set.setCharacterSetServer(charset);
        }
    }
}
