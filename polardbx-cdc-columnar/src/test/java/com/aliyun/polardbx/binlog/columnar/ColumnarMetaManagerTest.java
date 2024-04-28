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
import com.aliyun.polardbx.binlog.SpringContextHolder;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ColumnarMetaManagerTest {
    private static final String TEST_URL = "test_url";
    private static final String TEST_USERNAME = "test_username";
    private static final String TEST_PASSWORD = "test_password";
    private static final ColumnarNodeInfo TEST_LEADER_INFO =
        ColumnarNodeInfo.build("127.0.0.1:3070", "127.0.0.1@xxxx");
    private static final ColumnarNodeInfo TEST_NODE_INFO =
        ColumnarNodeInfo.build("127.0.0.1:3070", null);
    @Mock
    private MetaDbDataSource metaDbDataSource;
    @Mock
    private Connection metaDbConnection;
    @Mock
    private Statement statement;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    private final AtomicInteger metaDbConnInvalidateCount = new AtomicInteger(0);
    private final AtomicInteger metaDbConnValidateThrowAt = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicBoolean metaDbConnCloseFail = new AtomicBoolean(false);
    private final AtomicBoolean executeQueryFailed = new AtomicBoolean(false);
    private final AtomicBoolean resultSetHasNext = new AtomicBoolean(true);

    @Before
    public void setUp() throws SQLException {
        metaDbConnInvalidateCount.set(0);
        metaDbConnValidateThrowAt.set(Integer.MAX_VALUE);
        metaDbConnCloseFail.set(false);
        resultSetHasNext.set(true);
        executeQueryFailed.set(false);
        when(metaDbConnection.isValid(anyInt())).then(invocationOnMock -> {
            final int callCount = metaDbConnInvalidateCount.getAndDecrement();
            if (metaDbConnValidateThrowAt.get() == callCount) {
                throw new SQLException("Mock SQLException");
            } else {
                return callCount <= 0;
            }
        });
        doAnswer(invocationOnMock -> {
            if (metaDbConnCloseFail.get()) {
                throw new SQLException("Mock SQLException");
            }
            return null;
        }).when(metaDbConnection).close();
        when(metaDbConnection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(any())).then(invocationOnMock -> {
            if (executeQueryFailed.get()) {
                throw new SQLException("Mock SQLException");
            } else {
                return resultSet;
            }
        });
        when(metaDbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        doNothing().when(preparedStatement).setString(anyInt(), anyString());
        when(preparedStatement.executeQuery()).then(invocationOnMock -> {
            if (executeQueryFailed.get()) {
                throw new SQLException("Mock SQLException");
            } else {
                return resultSet;
            }
        });
        when(resultSet.next()).then(invocationOnMock -> resultSetHasNext.get());
        when(resultSet.getString(ArgumentMatchers.eq(1))).thenReturn(
            TEST_LEADER_INFO.getIp() + ":" + TEST_LEADER_INFO.getPort());
        when(resultSet.getString(ArgumentMatchers.eq(2))).thenReturn(TEST_LEADER_INFO.getName());
    }

    @Test
    public void getNodeInfo() {
        final ColumnarMetaManager columnarMetaManager = spy(ColumnarMetaManager.class);
        try (
            final MockedStatic<ColumnarMetaManager> columnarMetaManagerMocked = mockStatic(ColumnarMetaManager.class)) {
            columnarMetaManagerMocked.when(ColumnarMetaManager::getInstant).thenReturn(columnarMetaManager);
            final AtomicBoolean nullMetaDbConn = new AtomicBoolean(false);
            columnarMetaManagerMocked.when(ColumnarMetaManager::buildConn).thenAnswer(invocationOnMock -> {
                if (nullMetaDbConn.get()) {
                    return null;
                }
                return metaDbConnection;
            });

            // failed because getMetaDbConnection return null
            nullMetaDbConn.set(true);
            checkNodeResult(ColumnarNodeInfo.EMPTY);
            nullMetaDbConn.set(false);

            // get leader info success
            checkNodeResult(TEST_NODE_INFO);
            checkNodeResult(TEST_NODE_INFO, "11.167.60.147");
            checkNodeResult(TEST_NODE_INFO, "xxxx");

            // failed because getMetaDbConnection return null
            nullMetaDbConn.set(true);
            metaDbConnInvalidateCount.set(2);
            checkNodeResult(ColumnarNodeInfo.EMPTY);
            nullMetaDbConn.set(false);

            // failed because empty result set
            resultSetHasNext.set(false);
            checkNodeResult(ColumnarNodeInfo.EMPTY);

            // failed because execute query failed
            resultSetHasNext.set(true);
            executeQueryFailed.set(true);
            checkNodeResult(ColumnarNodeInfo.EMPTY);
        }
    }

    @Test
    public void testGetLeaderInfo() {
        final ColumnarMetaManager columnarMetaManager = spy(ColumnarMetaManager.class);
        try (
            final MockedStatic<ColumnarMetaManager> columnarMetaManagerMocked = mockStatic(ColumnarMetaManager.class)) {
            columnarMetaManagerMocked.when(ColumnarMetaManager::getInstant).thenReturn(columnarMetaManager);
            final AtomicBoolean nullMetaDbConn = new AtomicBoolean(false);
            columnarMetaManagerMocked.when(ColumnarMetaManager::buildConn).thenAnswer(invocationOnMock -> {
                if (nullMetaDbConn.get()) {
                    return null;
                }
                return metaDbConnection;
            });

            // failed because getMetaDbConnection return null
            nullMetaDbConn.set(true);
            checkLeaderResult(ColumnarNodeInfo.EMPTY);
            nullMetaDbConn.set(false);

            // get leader info success
            checkLeaderResult(TEST_LEADER_INFO);

            // failed because getMetaDbConnection return null
            nullMetaDbConn.set(true);
            metaDbConnInvalidateCount.set(2);
            checkLeaderResult(ColumnarNodeInfo.EMPTY);
            nullMetaDbConn.set(false);

            // failed because empty result set
            resultSetHasNext.set(false);
            checkLeaderResult(ColumnarNodeInfo.EMPTY);

            // failed because execute query failed
            resultSetHasNext.set(true);
            executeQueryFailed.set(true);
            checkLeaderResult(ColumnarNodeInfo.EMPTY);
        }
    }

    @Test
    public void testGetMetaDbConnection() {
        when(metaDbDataSource.getUrl()).thenReturn(TEST_URL);
        when(metaDbDataSource.getUsername()).thenReturn(TEST_USERNAME);
        when(metaDbDataSource.getPassword()).thenReturn(TEST_PASSWORD);

        try (final MockedStatic<SpringContextHolder> springContextHolderMocked = mockStatic(SpringContextHolder.class);
            final MockedStatic<DriverManager> driverManagerMocked = mockStatic(DriverManager.class)) {
            springContextHolderMocked.when(() -> SpringContextHolder.getObject(matches("metaDataSource")))
                .thenReturn(metaDbDataSource);

            AtomicBoolean driverGetConnectionFailed = new AtomicBoolean(false);

            driverManagerMocked
                .when(() -> DriverManager.getConnection(matches(TEST_URL), matches(TEST_USERNAME),
                    matches(TEST_PASSWORD)))
                .thenAnswer(invocationOnMock -> {
                    if (driverGetConnectionFailed.get()) {
                        throw new SQLException("Mock SQLException");
                    } else {
                        return metaDbConnection;
                    }
                });

            // buildConn and initMetaDbConn success
            checkLeaderResult(TEST_LEADER_INFO);

            // first time validate connection success
            checkLeaderResult(TEST_LEADER_INFO);

            // second time validate connection success
            metaDbConnInvalidateCount.set(1);
            checkLeaderResult(TEST_LEADER_INFO);

            // first time validate connection throw an exception
            // second time validate connection success
            metaDbConnInvalidateCount.set(1);
            metaDbConnValidateThrowAt.set(1);
            checkLeaderResult(TEST_LEADER_INFO);

            // first time validate connection throw an exception
            // second time validate connection failed
            metaDbConnInvalidateCount.set(2);
            metaDbConnValidateThrowAt.set(2);
            checkLeaderResult(TEST_LEADER_INFO);

            // first time validate connection failed
            // second time validate connection throw an exception
            metaDbConnInvalidateCount.set(2);
            metaDbConnValidateThrowAt.set(1);
            checkLeaderResult(TEST_LEADER_INFO);

            // first and second time validate connection failed
            // close invalidate connection failed
            metaDbConnValidateThrowAt.set(Integer.MAX_VALUE);
            metaDbConnInvalidateCount.set(2);
            metaDbConnCloseFail.set(true);
            checkLeaderResult(TEST_LEADER_INFO);

            // first and second time validate connection failed
            // close invalidate connection success
            // initMetaDbConn failed
            metaDbConnCloseFail.set(false);
            metaDbConnInvalidateCount.set(2);
            metaDbConnCloseFail.set(false);
            driverGetConnectionFailed.set(true);
            try {
                ColumnarMetaManager.getInstant().getLeaderInfo();
                MatcherAssert.assertThat("Should throw an exception", false);
            } catch (RuntimeException e) {
                MatcherAssert.assertThat(e.getMessage(), is("Create connection to meta db failed!"));
            }
        }
    }

    @Test
    public void testGetInstant() {
        MatcherAssert.assertThat(ColumnarMetaManager.getInstant(), notNullValue());
    }

    private void checkLeaderResult(ColumnarNodeInfo expected) {
        final ColumnarNodeInfo leaderInfo = ColumnarMetaManager.getInstant().getLeaderInfo();
        compareNodeInfo(expected, leaderInfo);
    }

    private void checkNodeResult(ColumnarNodeInfo expected, String... ip) {
        final ColumnarNodeInfo nodeInfo = ColumnarMetaManager.getInstant().getNodeInfo(ip);
        compareNodeInfo(expected, nodeInfo);
    }

    private static void compareNodeInfo(ColumnarNodeInfo expected, ColumnarNodeInfo nodeInfo) {
        MatcherAssert.assertThat(nodeInfo, equalTo(expected));
        MatcherAssert.assertThat(nodeInfo.getIp(), equalTo(expected.getIp()));
        MatcherAssert.assertThat(nodeInfo.getPort(), equalTo(expected.getPort()));
        MatcherAssert.assertThat(nodeInfo.getName(), equalTo(expected.getName()));
        MatcherAssert.assertThat(nodeInfo.toString(), equalTo(nodeInfo.toString()));
    }
}