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
package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import org.apache.commons.lang3.StringUtils;

/**
 * Position inside binlog file
 *
 * @author Seppo Jaakola
 * @version 1.0
 */
public class BinlogPosition extends LogPosition {

    private final static long[] pow10 = {
        1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000,
        10000000000L, 100000000000L, 1000000000000L, 10000000000000L,
        100000000000000L, 1000000000000000L, 10000000000000000L, 100000000000000000L,
        1000000000000000000L};
    /* The source server_id of position, 0 invalid */
    protected final long masterId;
    /* The timestamp, in seconds, 0 invalid */
    protected final long timestamp;

    protected long tso;

    protected String rtso;//真实tso，加虚拟段

    protected long innerOffset = -1;//一个 RowsLogEvent 可能包含多条改动，innerOffset 用以表示是第几条，-1 为未设置

    public BinlogPosition(String fileName, String rtso) {
        super(fileName);
        this.masterId = -1;
        this.timestamp = -1;
        this.rtso = rtso;
    }

    public BinlogPosition(String fileName, long position, long masterId, long timestamp) {
        super(fileName, position);
        this.masterId = masterId;
        this.timestamp = timestamp;
    }

    public BinlogPosition(LogPosition logPosition, long masterId, long timestamp) {
        super(logPosition.getFileName(), logPosition.getPosition());
        this.masterId = masterId;
        this.timestamp = timestamp;
    }

    public BinlogPosition(BinlogPosition binlogPosition) {
        super(binlogPosition.getFileName(), binlogPosition.getPosition());
        this.masterId = binlogPosition.masterId;
        this.timestamp = binlogPosition.timestamp;
    }

    public static String placeHolder(int bit, long number) {
        if (bit > 18) {
            throw new IllegalArgumentException("Bit must less than 18, but given " + bit);
        }

        final long max = pow10[bit];
        if (number >= max) {
            // 当 width < 数值的最大位数时，应该直接返回数值
            return String.valueOf(number);
        }

        return String.valueOf(max + number).substring(1);
    }

    public static BinlogPosition parseFromString(String source) {
        if (StringUtils.isBlank(source)) {
            return null;
        }
        // 格式： filename:position#masterid.timestamp
        int colonIndex = source.indexOf(':');
        int miscIndex = colonIndex + 1;
        int sharpIndex = source.indexOf('#', miscIndex);
        // NOTE: 向后兼容
        int semicolonIndex = source.indexOf(';', miscIndex);
        int dotIndex = source.lastIndexOf('.');
        // NOTE: 错误的位点
        if (colonIndex == -1) {
            return null;
        }

        String binlogSuffix = source.substring(0, colonIndex);
        long binlogPosition;
        if (sharpIndex != -1) {
            binlogPosition = Long.parseLong(source.substring(miscIndex, sharpIndex));
        } else if (semicolonIndex != -1) {
            // NOTE: 向后兼容
            binlogPosition = Long.parseLong(source.substring(miscIndex, semicolonIndex));
        } else if (dotIndex != -1 && dotIndex > colonIndex) {
            binlogPosition = Long.parseLong(source.substring(miscIndex, dotIndex));
        } else {
            binlogPosition = Long.parseLong(source.substring(miscIndex));
        }

        // NOTE: 默认值为 0
        long masterId = 0;
        if (sharpIndex != -1) {
            if (dotIndex != -1) {
                masterId = Long.parseLong(source.substring(sharpIndex + 1, dotIndex));
            } else {
                masterId = Long.parseLong(source.substring(sharpIndex + 1));
            }
        }

        long timestamp = 0; // NOTE: 默认值为 0
        if (dotIndex != -1 && dotIndex > colonIndex) {
            timestamp = Long.parseLong(source.substring(dotIndex + 1));
        }

        // NL
        return new BinlogPosition(binlogSuffix, binlogPosition,
            masterId,
            timestamp);
    }

    /**
     * Return BinlogPosition in String representation. This serves as EventId for DBMSEvent.
     */
    public String format2String(final int positionMaxLen) {
        String binlogSuffix = fileName;
        String binlogOffset = placeHolder((int) positionMaxLen, position);
        // 输出 '000001:0000000004@12+12314130'
        StringBuffer buf = new StringBuffer(40);
        buf.append(binlogSuffix);
        buf.append(':');
        buf.append(binlogOffset);
        if (masterId != 0) {
            buf.append('#');
            buf.append(masterId);
        }
        if (timestamp != 0) {
            buf.append('.');
            buf.append(timestamp);
        }
        if (tso != 0) {
            buf.append(".T(").append(tso).append(")");
        }
        if (StringUtils.isNotBlank(rtso)) {
            buf.append(".rtso(").append(rtso).append(")");
        }
        if (innerOffset >= 0) {
            buf.append('#');
            buf.append(innerOffset);
        }
        return buf.toString();
    }

    public String getFilePattern() {
        final int index = fileName.indexOf('.');
        if (index != -1) {
            return fileName.substring(0, index);
        }
        return null;
    }

    public void setFilePattern(String filePattern) {
        // We tolerate the event ID with or without the binlog prefix.
        if (fileName.indexOf('.') < 0) {
            fileName = filePattern + '.' + fileName;
        }
    }

    public long getMasterId() {
        return masterId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getTso() {
        return tso;
    }

    public void setTso(long tso) {
        this.tso = tso;
    }

    public String getRtso() {
        return rtso;
    }

    public void setRtso(String rtso) {
        this.rtso = rtso;
    }

    public long getInnerOffset() {
        return innerOffset;
    }

    public void setInnerOffset(long innerOffset) {
        this.innerOffset = innerOffset;
    }

    @Override
    public String toString() {
        return format2String(10);
    }
}
