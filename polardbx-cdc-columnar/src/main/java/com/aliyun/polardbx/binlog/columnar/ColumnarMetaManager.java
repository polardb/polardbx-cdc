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
package com.aliyun.polardbx.binlog.columnar;

import com.aliyun.polardbx.binlog.MetaDbDataSource;
import com.aliyun.polardbx.binlog.util.AddressUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

@Slf4j
public class ColumnarMetaManager {
    private static ColumnarMetaManager INSTANT;
    private volatile Connection metaDbConn;

    private ColumnarMetaManager() {
    }

    public ColumnarNodeInfo getLeaderInfo() {
        final Connection metaDbConn = getMetaDbConnection();
        if (metaDbConn == null) {
            log.error("connection is null, return null directly");
            return ColumnarNodeInfo.EMPTY;
        }

        try (Statement statement = metaDbConn.createStatement()) {
            final ResultSet rs = statement.executeQuery(
                "select a.owner as ipport, c.leader as leader "
                    + "from columnar_lease a "
                    + "join (select b.id as leaderId, b.owner as leader, SUBSTRING_INDEX(b.owner, '@', 1) as leaderIp from columnar_lease b order by id limit 1) c "
                    + "on a.id != c.leaderId and SUBSTRING_INDEX(a.owner, ':', 1) = c.leaderIp order by a.lease desc limit 1");
            if (rs.next()) {
                return ColumnarNodeInfo.build(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            log.error("Query columnar leader information failed!", e);
        }
        return ColumnarNodeInfo.EMPTY;
    }

    public ColumnarNodeInfo getNodeInfo(String... ips) {
        final Connection metaDbConn = getMetaDbConnection();
        if (metaDbConn == null) {
            log.error("connection is null, return null directly");
            return ColumnarNodeInfo.EMPTY;
        }

        final Set<String> normalizedIpSet = normalizeIp(ips);
        if (normalizedIpSet.isEmpty() || (normalizedIpSet.size() == 1 && normalizedIpSet.contains("127.0.0.1"))) {
            Optional.ofNullable(AddressUtil.getHostAddress().getHostAddress())
                .ifPresent(normalizedIpSet::add);
        }

        final String query =
            "select owner from columnar_lease a where a.id > 1 and SUBSTRING_INDEX(a.owner, ':', 1) in ( "
                + IntStream.range(0, normalizedIpSet.size()).mapToObj(i -> "?").collect(Collectors.joining(","))
                + ") order by id desc limit 1";

        try (PreparedStatement statement = metaDbConn.prepareStatement(query)) {
            int paramIndex = 1;
            for (String ip : normalizedIpSet) {
                statement.setString(paramIndex++, ip);
            }
            final ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return ColumnarNodeInfo.build(rs.getString(1), null);
            }
        } catch (SQLException e) {
            log.error("Query columnar node information failed!", e);
        }
        return ColumnarNodeInfo.EMPTY;
    }

    private Set<String> normalizeIp(String[] ips) {
        final HashSet<String> result = new HashSet<>();
        for (String ip : ips) {
            try {
                // 将字符串解析为 InetAddress 对象，这将处理任何内部的标准化逻辑
                InetAddress inetAddress = InetAddress.getByName(ip);

                // 返回标准化后的 IP 地址字符串
                result.add(inetAddress.getHostAddress());
            } catch (UnknownHostException e) {
                // IP 地址不合法
                log.warn("Invalid IP address format.", e);
            }
        }

        return result;
    }

    public static ColumnarMetaManager getInstant() {
        if (null == INSTANT) {
            synchronized (ColumnarMetaManager.class) {
                if (null == INSTANT) {
                    INSTANT = init();
                }
            }
        }

        return INSTANT;
    }

    private static ColumnarMetaManager init() {
        return new ColumnarMetaManager();
    }

    private Connection getMetaDbConnection() {
        if (this.metaDbConn == null) {
            return initMetaDbConn();
        }

        boolean doReInit = true;
        try {
            doReInit = !this.metaDbConn.isValid(1);
        } catch (SQLException e) {
            log.error("Check metaDb connection failed, try rebuild connection", e);
        }

        if (doReInit) {
            return reInitMetaDbConn();
        }

        return this.metaDbConn;
    }

    private synchronized Connection reInitMetaDbConn() {
        try {
            if (this.metaDbConn.isValid(1)) {
                return this.metaDbConn;
            }
        } catch (SQLException e) {
            log.error("Check metaDb connection failed, try rebuild connection", e);
        }

        try {
            if (null != this.metaDbConn) {
                this.metaDbConn.close();
            }
        } catch (SQLException e) {
            log.error("Close metaDb connection failed, try create new connection", e);
        } finally {
            this.metaDbConn = null;
        }
        return initMetaDbConn();
    }

    private synchronized Connection initMetaDbConn() {
        if (this.metaDbConn == null) {
            this.metaDbConn = buildConn();
        }
        return this.metaDbConn;
    }

    static Connection buildConn() {
        try {
            MetaDbDataSource metaDs = getObject("metaDataSource");
            return DriverManager.getConnection(metaDs.getUrl(), metaDs.getUsername(), metaDs.getPassword());
        } catch (SQLException e) {
            log.error("ColumnarMetaManager init connection fail", e);
            throw new RuntimeException("Create connection to meta db failed!", e);
        }
    }
}
