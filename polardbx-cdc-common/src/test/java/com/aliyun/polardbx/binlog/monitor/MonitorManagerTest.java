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
