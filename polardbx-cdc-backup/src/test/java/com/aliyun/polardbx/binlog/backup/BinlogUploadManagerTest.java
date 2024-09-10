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
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.MetaDbDataSource;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author yudong
 * @since 2024/7/25 13:51
 **/
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogUploadManagerTest {
    @Mock
    private MetaDbDataSource metaDs;
    @Mock
    private BinlogOssRecordMapper mapper;
    @Mock
    private Connection conn;
    @Mock
    private Statement stmt;
    @Mock
    private BinlogOssRecord record;
    @Mock
    private BinlogUploadManager manager;
    @Mock
    private ResultSet resultSet;
    private MockedStatic<SpringContextHolder> springContextHolder;
    private MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig;
    private static final ScheduledThreadPoolExecutor keepAliveExecutor = new ScheduledThreadPoolExecutor(1);
    private static final String binlogFileName = "binlog.000001";
    private static final String clusterId = "cluster_1";
    private static final String lockName = "uploadFile1";
    private static final String LOCK_SQL = "SELECT GET_LOCK('" + lockName + "',1)";

    @Before
    @SneakyThrows
    public void before() {
        springContextHolder = mockStatic(SpringContextHolder.class);
        dynamicApplicationConfig = mockStatic(DynamicApplicationConfig.class);
        springContextHolder.when(() -> SpringContextHolder.getObject("metaDataSource")).thenReturn(metaDs);
        springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordMapper.class)).thenReturn(mapper);
        dynamicApplicationConfig.when(() -> DynamicApplicationConfig.getString(ConfigKeys.INST_IP))
            .thenReturn("127.1");
        dynamicApplicationConfig.when(() -> DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))
            .thenReturn(clusterId);
        when(metaDs.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(record.getBinlogFile()).thenReturn(binlogFileName);
        when(record.getId()).thenReturn(1);
        when(manager.getRecordMapper()).thenReturn(mapper);
        when(manager.getKeepAliveExecutor()).thenReturn(keepAliveExecutor);
        Mockito.doCallRealMethod().when(manager).getLockName(1);
        Mockito.doCallRealMethod().when(manager).globalLock(record, conn);
        Mockito.doCallRealMethod().when(manager).globalUnLock(record, conn);
        Mockito.doCallRealMethod().when(manager).processUpload(Mockito.any(BinlogOssRecord.class));
        Mockito.doCallRealMethod().when(manager).setUploadStatusToUploading(record);
        Mockito.doCallRealMethod().when(manager).setUploadStatusToSuccess(record);
    }

    @After
    public void after() {
        springContextHolder.close();
        dynamicApplicationConfig.close();
    }

    @Test
    @SneakyThrows
    public void testProcessUpload_lock_conflict() {
        Connection connection = buildConnection();
        when(manager.getConnection()).thenReturn(connection);
        
        SQLException lockException =
            new SQLException("Lock wait timeout exceeded; try restarting transaction", "", 1205);
        when(stmt.executeQuery(LOCK_SQL)).thenThrow(lockException);
        manager.processUpload(record);
        verify(stmt, times(1)).executeQuery(LOCK_SQL);
        verify(manager, times(0)).setUploadStatusToUploading(record);
    }

    @Test(expected = SQLException.class)
    @SneakyThrows
    public void testProcessUpload_meet_other_exception() {
        Connection connection = buildConnection();
        when(manager.getConnection()).thenReturn(connection);

        SQLException lockException =
            new SQLException("Lock wait timeout exceeded; try restarting transaction", "", 1111);
        when(stmt.executeQuery(LOCK_SQL)).thenThrow(lockException);
        manager.processUpload(record);
    }

    @Test
    @SneakyThrows
    public void testProcessUpload_all_ok() {
        Connection connection = buildConnection();
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);
        when(stmt.executeQuery(LOCK_SQL)).thenReturn(resultSet);
        when(manager.getConnection()).thenReturn(connection);

        Mockito.doNothing().when(manager).deleteFileOnRemote(record);
        Mockito.doNothing().when(manager).doUpload(record);

        Mockito.doCallRealMethod().when(manager).processUpload(Mockito.any(BinlogOssRecord.class));
        manager.processUpload(record);
        verify(stmt, times(2)).executeQuery(anyString());
        verify(manager, times(1)).setUploadStatusToUploading(record);
        verify(manager, times(1)).setUploadStatusToSuccess(record);
    }

    private Connection buildConnection() {
        MetaDbDataSource metaDs = getObject("metaDataSource");
        return metaDs.getConnection();
    }
}
