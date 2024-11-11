/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public class JunitListener extends RunListener {

    private static final Log log = LogFactory.getLog(JdbcUtil.class);
    private static final int maxSize = 20;

    long failure = 0;
    long ignored = 0;
    long finish = 0;
    Map<Description, Long> cacheDescription = new HashMap<>();
    TreeSet<TestDescription> descriptionSet = new TreeSet<>((o1, o2) -> o1.takeTime > o2.takeTime ? 1 : -1);

    public void testStarted(Description description) throws Exception {
        cacheDescription.put(description, System.currentTimeMillis());
    }

    public void testFinished(Description description) throws Exception {
        Long startTime = cacheDescription.remove(description);
        if (startTime != null) {
            descriptionSet.add(new TestDescription(description, System.currentTimeMillis() - startTime));
            if (descriptionSet.size() > maxSize) {
                descriptionSet.pollFirst();
            }
        }
        finish++;
        System.out.println(
            "[" + Thread.currentThread().getName() + "]" + " Finish " + description.getClassName() + "." + description
                .getMethodName() + " Task " + (System.currentTimeMillis() - startTime) + " millis");
        System.out
            .println("Test failed: " + failure + ", passed: " + (finish - ignored - failure) + ", ignored: " + ignored);

    }

    public void testFailure(Failure failure) throws Exception {
        this.failure++;
    }

    public void testIgnored(Description description) throws Exception {
        this.ignored++;
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        for (TestDescription testDescription : descriptionSet) {
            System.out.println(
                String.format("The class %s take %d milliseconds", testDescription.description.getDisplayName(),
                    testDescription.takeTime));
        }

    }

    public class TestDescription {
        private Description description;
        private long takeTime;

        public TestDescription(Description description, long takeTime) {
            this.description = description;
            this.takeTime = takeTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TestDescription)) {
                return false;
            }
            TestDescription that = (TestDescription) o;
            return description.equals(that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(description);
        }
    }
}
