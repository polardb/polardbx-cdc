/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.alibaba.polardbx.core.cj.NativeSession;
import com.alibaba.polardbx.core.cj.jdbc.ConnectionImpl;
import com.alibaba.polardbx.core.cj.protocol.Protocol;
import com.alibaba.polardbx.core.cj.protocol.SocketConnection;
import org.junit.Assert;
import org.junit.Test;

public class DirectLogFetcherCompatibilityTest {

    @Test
    public void testPolarDbxConnectorInternalApiShape() throws Exception {
        Assert.assertNotNull(ConnectionImpl.class.getMethod("getSession"));
        Assert.assertNotNull(NativeSession.class.getMethod("getProtocol"));
        Assert.assertNotNull(Protocol.class.getMethod("getSocketConnection"));
        Assert.assertNotNull(SocketConnection.class.getMethod("getMysqlOutput"));
        Assert.assertNotNull(SocketConnection.class.getMethod("getMysqlInput"));
    }
}
