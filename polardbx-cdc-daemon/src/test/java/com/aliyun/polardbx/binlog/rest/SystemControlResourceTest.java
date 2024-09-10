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
package com.aliyun.polardbx.binlog.rest;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.rest.resources.SystemControlResource;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;

public class SystemControlResourceTest extends BaseTestWithGmsTables {

    @Test
    public void testGetVersion() {
        setConfig(ConfigKeys.RELEASE_NOTE_PATH, this.getClass().getClassLoader().getResource("releaseNote").getFile());

        SystemControlResource resource = Mockito.mock(SystemControlResource.class);
        Mockito.when(resource.getVersion()).thenCallRealMethod();
        String result = resource.getVersion();
        Assert.assertNotNull(result);
        // 返回 x.y.z-date-buildNum
        Assert.assertTrue(result.matches("\\d+\\.\\d+\\.\\d+\\-\\d{8}_\\d{8}"));
    }

    @Test
    public void testClean() throws NoSuchFieldException, IllegalAccessException {
        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        TransactionTemplate polarxTransactionTemplate = Mockito.mock(TransactionTemplate.class);
        Mockito.doNothing().when(polarxJdbcTemplate).execute(Mockito.anyString());
        Mockito.when(polarxTransactionTemplate.execute(t -> null)).thenReturn(null);

        Field applicationContextField = SpringContextHolder.class.getDeclaredField("applicationContext");
        applicationContextField.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) applicationContextField.get(null);
        DefaultListableBeanFactory beanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        beanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);
        beanFactory.registerSingleton("polarxTransactionTemplate", polarxTransactionTemplate);
        setConfig(ConfigKeys.DAEMON_CLEAN_INTERFACE_FORCE_CHECK_CLUSTER_ENABLED, "true");
        SystemControlResource resource = Mockito.mock(SystemControlResource.class);
        MockedStatic<RuntimeLeaderElector> runtimeLeaderElector = Mockito.mockStatic(RuntimeLeaderElector.class);
        runtimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
        Assert.assertTrue(RuntimeLeaderElector.isDaemonLeader());
        String clusterId = null;
        Mockito.when(resource.clean(clusterId)).thenCallRealMethod();
        Throwable t = null;
        try {
            resource.clean(clusterId);
        } catch (PolardbxException e) {
            t = e;
        }
        Assert.assertNotNull(t);
        Assert.assertEquals("clean operation check cluster id failed!", t.getMessage());
        resource.clean(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));

        setConfig(ConfigKeys.DAEMON_CLEAN_INTERFACE_FORCE_CHECK_CLUSTER_ENABLED, "false");
        resource.clean(clusterId);
    }
}
