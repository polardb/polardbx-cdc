/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.monitor;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;

/**
 * Created by ziyang.lb
 **/
public class MonitorManagerTest extends BaseTest {

    @Test
    public void test() {
        System.setProperty("taskName", "Final");
        MonitorManager.getInstance().startup();

        try {
            get();
        } catch (Throwable t) {
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.MERGER_STAGE_LOOP_ERROR, ExceptionUtils.getStackTrace(t));
        }

        MonitorManager.getInstance().triggerAlarm(MonitorType.MERGER_STAGE_EMPTY_LOOP_EXCEED_THRESHOLD, 10);
        MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_DELAY, 100000);
        MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_DELAY, 100000);

        try {
            get();
        } catch (Throwable t) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_FILE_GENERATE_ERROR,
                ExceptionUtils.getStackTrace(t));
        }

        try {
            get();
        } catch (Throwable t) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_FILE_SYNC_ERROR,
                ExceptionUtils.getStackTrace(t));
        }
    }

    private void get() {
        try {
            getSubException();
        } catch (Throwable t) {
            throw new PolardbxException(
                "detected disorderly tso，current tso is " + "123456" + ",last tso is " + "123457", t);
        }
    }

    private void getSubException() {
        throw new RuntimeException("报警测试");
    }
}
