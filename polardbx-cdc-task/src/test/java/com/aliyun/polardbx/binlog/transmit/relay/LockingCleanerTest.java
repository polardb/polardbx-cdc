/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockingCleanerTest {

    private LockingCleaner lockingCleaner;

    @Before
    public void setUp() {
        lockingCleaner = new LockingCleaner();
    }

    // ========== cleanWithCallback tests ==========

    @Test
    public void testCleanWithCallback_Normal() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter("0000000000000000001", "0000000000000000002");

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("supplier should be called when cleaningTso < maxReadTso", supplierCalled.get());
    }

    @Test
    public void testCleanWithCallback_SkipWhenMaxReadTsoBlank() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter("0000000000000000001", "");

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertFalse("supplier should NOT be called when maxReadTso is blank", supplierCalled.get());
    }

    @Test
    public void testCleanWithCallback_SkipWhenMaxReadTsoNull() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter("0000000000000000001", null);

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertFalse("supplier should NOT be called when maxReadTso is null", supplierCalled.get());
    }

    @Test
    public void testCleanWithCallback_SkipWhenCleaningTsoEqualsMaxReadTso() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        String sameTso = "0000000000000000001";
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter(sameTso, sameTso);

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertFalse("supplier should NOT be called when cleaningTso == maxReadTso", supplierCalled.get());
    }

    @Test
    public void testCleanWithCallback_SkipWhenCleaningTsoGreaterThanMaxReadTso() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter("0000000000000000002", "0000000000000000001");

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertFalse("supplier should NOT be called when cleaningTso > maxReadTso", supplierCalled.get());
    }

    // ========== checkWithCallback tests ==========

    @Test
    public void testCheckWithCallback_ValidWhenRequestTsoGreaterThanMaxCleanTso() throws InvalidTsoException {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter("0000000000000000002", "0000000000000000001");

        lockingCleaner.checkWithCallback(() -> parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("supplier should be called when requestTso > maxCleanTso", supplierCalled.get());
    }

    @Test
    public void testCheckWithCallback_ValidWhenRequestTsoEqualsMaxCleanTso() throws InvalidTsoException {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        String sameTso = "0000000000000000001";
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter(sameTso, sameTso);

        lockingCleaner.checkWithCallback(() -> parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("supplier should be called when requestTso == maxCleanTso", supplierCalled.get());
    }

    @Test
    public void testCheckWithCallback_ValidWhenMaxCleanTsoBlank() throws InvalidTsoException {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter("0000000000000000001", "");

        lockingCleaner.checkWithCallback(() -> parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("supplier should be called when maxCleanTso is blank", supplierCalled.get());
    }

    @Test
    public void testCheckWithCallback_ValidWhenMaxCleanTsoNull() throws InvalidTsoException {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter("0000000000000000001", null);

        lockingCleaner.checkWithCallback(() -> parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("supplier should be called when maxCleanTso is null", supplierCalled.get());
    }

    @Test(expected = InvalidTsoException.class)
    public void testCheckWithCallback_InvalidTso() throws InvalidTsoException {
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter("0000000000000000001", "0000000000000000002");

        lockingCleaner.checkWithCallback(() -> parameter, () -> null);
    }

    @Test
    public void testCheckWithCallback_ParameterSupplierCalledUnderLock() throws InvalidTsoException {
        AtomicInteger supplierCallCount = new AtomicInteger(0);
        AtomicBoolean parameterSupplierCalledFirst = new AtomicBoolean(false);

        lockingCleaner.checkWithCallback(() -> {
            parameterSupplierCalledFirst.set(supplierCallCount.get() == 0);
            return new LockingCleaner.CheckParameter("0000000000000000002", "");
        }, () -> {
            supplierCallCount.incrementAndGet();
            return null;
        });

        assertTrue("parameterSupplier should be called before supplier", parameterSupplierCalledFirst.get());
        assertEquals(1, supplierCallCount.get());
    }

    // ========== Data class tests ==========

    @Test
    public void testCleanParameter() {
        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter("tso1", "tso2");
        assertEquals("tso1", parameter.getCleaningTso());
        assertEquals("tso2", parameter.getMaxReadTso());
    }

    @Test
    public void testCheckParameter() {
        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter("reqTso", "maxCleanTso");
        assertEquals("reqTso", parameter.getRequestTso());
        assertEquals("maxCleanTso", parameter.getMaxCleanTso());
    }

    // ========== TSO length comparison scenarios ==========

    @Test
    public void testCleanWithCallback_19DigitVs38DigitTso() {
        // computeTsoBefore returns 19-digit TSO, checkpointLastTso is 38-digit
        // Same physical timestamp: 19-digit < 38-digit in string comparison
        // cleaningTso(19) < maxReadTso(38) with same prefix → should proceed

        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        String tso19 = "7000000000000000001";
        String tso38 = "7000000000000000001000000000000000002";

        LockingCleaner.CleanParameter parameter =
            new LockingCleaner.CleanParameter(tso19, tso38);

        lockingCleaner.cleanWithCallback(parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("19-digit cleanupTso < 38-digit maxReadTso with same prefix → should clean",
            supplierCalled.get());
    }

    @Test
    public void testCheckWithCallback_19DigitVs38DigitTso_SameTimestamp() throws InvalidTsoException {
        // Same physical timestamp: 38-digit requestTso > 19-digit maxCleanTso → valid

        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        String requestTso38 = "7000000000000000001000000000000000000";
        String maxCleanTso19 = "7000000000000000001";

        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter(requestTso38, maxCleanTso19);

        lockingCleaner.checkWithCallback(() -> parameter, () -> {
            supplierCalled.set(true);
            return null;
        });

        assertTrue("38-digit requestTso > 19-digit maxCleanTso with same prefix → valid",
            supplierCalled.get());
    }

    @Test(expected = InvalidTsoException.class)
    public void testCheckWithCallback_19DigitRequestLessThan38DigitMaxClean() throws InvalidTsoException {
        // 19-digit requestTso < 38-digit maxCleanTso with same timestamp prefix
        // 19-digit is shorter → string comparison: shorter < longer → InvalidTsoException

        String requestTso19 = "7000000000000000001";
        String maxCleanTso38 = "7000000000000000001000000000000000000";

        LockingCleaner.CheckParameter parameter =
            new LockingCleaner.CheckParameter(requestTso19, maxCleanTso38);

        lockingCleaner.checkWithCallback(() -> parameter, () -> null);
    }
}
