/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Decoder for canonical BlobRef text, with full payload validation for V2.
 */
public final class ExternalColumnBlobRef {

    private static final int VERSION = 2;
    private static final int BINARY_LENGTH = 33;
    private static final int TEXT_LENGTH = BINARY_LENGTH * 2;
    private static final int RAW_SIZE_OFFSET = 13;
    private static final int MD5_OFFSET = 17;
    private static final int MD5_LENGTH = 16;
    private static final int MAX_RAW_SIZE = 256 * 1024 * 1024;
    private final int seqId;
    private final long slotAddr;
    private final int rawSize;
    private final byte[] rawMd5;

    private ExternalColumnBlobRef(int seqId, long slotAddr, int rawSize, byte[] rawMd5) {
        this.seqId = seqId;
        this.slotAddr = slotAddr;
        this.rawSize = rawSize;
        this.rawMd5 = rawMd5;
    }

    public static ExternalColumnBlobRef decode(String encoded) {
        if (encoded == null || encoded.length() < 2 || (encoded.length() & 1) != 0) {
            throw new PolardbxException("invalid external-column BlobRef text length");
        }
        validateCanonicalHex(encoded);
        int observedVersion = decodeHexByte(encoded.charAt(0), encoded.charAt(1));
        if (observedVersion != VERSION) {
            throw new UnsupportedExternalColumnBlobRefVersionException(observedVersion, VERSION);
        }
        if (encoded.length() != TEXT_LENGTH) {
            throw new PolardbxException("invalid external-column BlobRef V2 text length");
        }
        byte[] raw = decodeCanonicalHex(encoded);
        int seqId = getInt(raw, 1);
        if (seqId < 0) {
            throw new PolardbxException("invalid negative external-column BlobRef seqId");
        }
        long slotAddr = getLong(raw, 5);
        // CDC joins the complete address with its transaction-local raw-data carrier. Page/slot
        // bit allocation and Page chunk layout are storage-engine details and must stay opaque here.
        if (slotAddr == 0) {
            throw new PolardbxException(
                "invalid zero external-column BlobRef address");
        }
        long unsignedRawSize = getInt(raw, RAW_SIZE_OFFSET) & 0xFFFF_FFFFL;
        if (unsignedRawSize > MAX_RAW_SIZE) {
            throw new PolardbxException("external-column BlobRef raw size exceeds supported limit: "
                + unsignedRawSize);
        }
        return new ExternalColumnBlobRef(seqId, slotAddr, (int) unsignedRawSize,
            Arrays.copyOfRange(raw, MD5_OFFSET, MD5_OFFSET + MD5_LENGTH));
    }

    public void validateRaw(byte[] data) {
        if (data == null || data.length != rawSize) {
            throw new PolardbxException("external-column staging raw size mismatch for seqId " + seqId
                + ", slotAddr " + Long.toUnsignedString(slotAddr) + ", expected " + rawSize
                + ", actual " + (data == null ? -1 : data.length));
        }
        if (!Arrays.equals(rawMd5, md5(data))) {
            throw new PolardbxException("external-column staging raw MD5 mismatch for seqId " + seqId
                + ", slotAddr " + Long.toUnsignedString(slotAddr));
        }
    }

    public int getSeqId() {
        return seqId;
    }

    public long getSlotAddr() {
        return slotAddr;
    }

    private static void validateCanonicalHex(String encoded) {
        for (int i = 0; i < encoded.length(); i++) {
            if (!isCanonicalHex(encoded.charAt(i))) {
                throw new PolardbxException("invalid non-canonical external-column BlobRef hex character");
            }
        }
    }

    private static byte[] decodeCanonicalHex(String encoded) {
        byte[] value = new byte[BINARY_LENGTH];
        for (int i = 0; i < encoded.length(); i += 2) {
            char highChar = encoded.charAt(i);
            char lowChar = encoded.charAt(i + 1);
            value[i / 2] = (byte) decodeHexByte(highChar, lowChar);
        }
        return value;
    }

    private static int decodeHexByte(char highChar, char lowChar) {
        return Character.digit(highChar, 16) << 4 | Character.digit(lowChar, 16);
    }

    private static boolean isCanonicalHex(char value) {
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f';
    }

    private static int getInt(byte[] value, int offset) {
        return (value[offset] & 0xFF) << 24
            | (value[offset + 1] & 0xFF) << 16
            | (value[offset + 2] & 0xFF) << 8
            | value[offset + 3] & 0xFF;
    }

    private static long getLong(byte[] value, int offset) {
        return (long) (value[offset] & 0xFF) << 56
            | (long) (value[offset + 1] & 0xFF) << 48
            | (long) (value[offset + 2] & 0xFF) << 40
            | (long) (value[offset + 3] & 0xFF) << 32
            | (long) (value[offset + 4] & 0xFF) << 24
            | (long) (value[offset + 5] & 0xFF) << 16
            | (long) (value[offset + 6] & 0xFF) << 8
            | (long) (value[offset + 7] & 0xFF);
    }

    private static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new PolardbxException("MD5 algorithm is unavailable", e);
        }
    }
}
