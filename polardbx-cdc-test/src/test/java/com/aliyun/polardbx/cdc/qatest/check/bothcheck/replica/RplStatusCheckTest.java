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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RplStatusCheckTest extends RplBaseTestCase {

    @Test
    public void testSlaveStatus() throws ExecutionException, RetryException {
        check(getPolardbxConnection());
    }

    @Test
    public void testBackwardStatus() throws ExecutionException, RetryException {
        check(getCdcSyncDbConnection());
    }

    private void check(Connection conn) throws ExecutionException, RetryException {
        Retryer<Object> retryer = RetryerBuilder.newBuilder().
            retryIfExceptionOfType(Throwable.class).
            withRetryListener(new RetryListener() {
                @Override
                public <V> void onRetry(Attempt<V> attempt) {
                    if (attempt.hasException()) {
                        log.error("retry with count ： " + attempt.getAttemptNumber(), attempt.getExceptionCause());
                    }
                }
            }).
            withStopStrategy(StopStrategies.stopAfterAttempt(24)).
            withWaitStrategy(
                WaitStrategies.fixedWait(10, TimeUnit.SECONDS)).
            withBlockStrategy(BlockStrategies.threadSleepStrategy()).build();

        retryer.call(() -> {
            doCheck(conn);
            return null;
        });
    }

    private void doCheck(Connection conn) throws SQLException {
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("show slave status");
        int count = 0;
        while (rs.next()) {
            String lastError = rs.getString("Last_Error");
            String sbm = rs.getString("Seconds_Behind_Master");
            Assert.assertTrue("show slave status check error ! " + lastError, StringUtils.isBlank(lastError));
            Assert.assertTrue("second behind is not number " + sbm, NumberUtils.isCreatable(sbm));
            Assert.assertTrue("second behind master more than 5 min" + sbm,
                NumberUtils.toInt(sbm) < TimeUnit.MINUTES.toSeconds(5));
            count++;
        }
        Assert.assertNotEquals("execute show slave status failed!, result set is empty", 0, count);

    }

}
