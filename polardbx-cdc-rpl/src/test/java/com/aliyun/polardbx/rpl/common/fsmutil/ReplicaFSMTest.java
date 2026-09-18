/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * 创建replica状态机失败时，不能静默返回ERROR_FSMID，
 * 否则内层事务已被标记rollback-only，客户端只能拿到UnexpectedRollbackException，真实原因丢失
 */
public class ReplicaFSMTest extends BaseTest {

    private final MetaManagerTranProxy proxy = Mockito.mock(MetaManagerTranProxy.class);

    private Field managerField;
    private MetaManagerTranProxy originManager;

    @Before
    public void setUp() throws Exception {
        registerSpringObject("metaManagerTranProxy", proxy);
        managerField = ReplicaFSM.class.getDeclaredField("manager");
        managerField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(managerField, managerField.getModifiers() & ~Modifier.FINAL);
        originManager = (MetaManagerTranProxy) managerField.get(null);
        managerField.set(null, proxy);
    }

    @After
    public void tearDown() throws Exception {
        managerField.set(null, originManager);
    }

    @Test
    public void testCreate_InitStateMachineFailed_ThrowExceptionWithRealCause() throws Throwable {
        IllegalArgumentException realCause = new IllegalArgumentException("bound must be positive");
        when(proxy.initStateMachine(eq(StateMachineType.REPLICA), any(ReplicaMeta.class), any(ReplicaFSM.class)))
            .thenThrow(realCause);

        try {
            ReplicaFSM.getInstance().create(new ReplicaMeta());
            Assert.fail("should throw exception instead of returning ERROR_FSMID silently");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("bound must be positive"));
            Assert.assertSame(realCause, e.getCause());
        }
    }

    @Test
    public void testCreate_Success_ReturnStateMachineId() throws Throwable {
        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(88L);
        when(proxy.initStateMachine(eq(StateMachineType.REPLICA), any(ReplicaMeta.class), any(ReplicaFSM.class)))
            .thenReturn(stateMachine);

        Assert.assertEquals(88L, ReplicaFSM.getInstance().create(new ReplicaMeta()));
    }
}
