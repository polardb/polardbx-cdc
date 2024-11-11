/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.client;

import java.io.IOException;

/**
 * Created by ziyang.lb
 **/
public interface PacketReceiver {

    void onReceive(byte[] packet, boolean isHeartBeat) throws IOException;
}
