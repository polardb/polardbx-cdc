/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.error.PolardbxException;

/**
 * Signals a canonical BlobRef string whose version is not understood by this CDC binary.
 */
public class UnsupportedExternalColumnBlobRefVersionException extends PolardbxException {

    private final int observedVersion;
    private final int supportedVersion;

    public UnsupportedExternalColumnBlobRefVersionException(int observedVersion, int supportedVersion) {
        super("unsupported external-column BlobRef version " + observedVersion
            + ", supported version " + supportedVersion);
        this.observedVersion = observedVersion;
        this.supportedVersion = supportedVersion;
    }

    public int getObservedVersion() {
        return observedVersion;
    }

    public int getSupportedVersion() {
        return supportedVersion;
    }
}
