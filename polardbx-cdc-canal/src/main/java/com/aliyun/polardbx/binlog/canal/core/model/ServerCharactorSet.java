/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.canal.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServerCharactorSet {

    private String characterSetClient;
    private String characterSetConnection;
    private String characterSetDatabase;
    private String characterSetServer;

    public String getCharacterSetClient() {
        return characterSetClient;
    }

    public void setCharacterSetClient(String characterSetClient) {
        this.characterSetClient = characterSetClient;
    }

    public String getCharacterSetConnection() {
        return characterSetConnection;
    }

    public void setCharacterSetConnection(String characterSetConnection) {
        this.characterSetConnection = characterSetConnection;
    }

    public String getCharacterSetDatabase() {
        return characterSetDatabase;
    }

    public void setCharacterSetDatabase(String characterSetDatabase) {
        this.characterSetDatabase = characterSetDatabase;
    }

    public String getCharacterSetServer() {
        return characterSetServer;
    }

    public void setCharacterSetServer(String characterSetServer) {
        this.characterSetServer = characterSetServer;
    }
}
