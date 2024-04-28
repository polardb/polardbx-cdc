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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.rpc.cdc.BinlogEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet.loadCharactorSetFromCN;

/**
 * Created by ShuGuang
 */
@Slf4j
public class BinlogEventReader {
    private static final int EVENT_MAX_SIZE = 16 * 1024 * 1024;//最大4G，这里为节省内存，先设置为16M

    private final String fileName;
    private final long pos;
    private final long offset;
    private final BinlogFileReadChannel channel;
    private final ByteBuffer buffer = ByteBuffer.allocate(EVENT_MAX_SIZE);
    private final LogContext context = new LogContext();
    private final LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
    private long rowCount;
    private long fp;

    public BinlogEventReader(CdcFile cdcFile, long pos, long offset, long rowCount)
        throws IOException {
        this.fileName = cdcFile.getName();
        this.pos = pos < 0 ? 0 : pos;
        this.offset = offset < 0 ? 0 : offset;
        this.rowCount = rowCount < 0 ? Integer.MAX_VALUE : rowCount;
        this.context.setLogPosition(new LogPosition(fileName, pos));
        this.context.setServerCharactorSet(loadCharactorSetFromCN());
        this.channel = cdcFile.getReadChannel();
        log.info("[fixed] show binlog events in {} from {} limit {}, {}", fileName, this.pos, this.offset,
            this.rowCount);
    }

    /**
     * 检查一下指定的position是否合法，以及位于position的这个event是否合法
     */
    public void valid() throws IOException {
        long size = channel.size();
        if (size < pos) {
            throw new PolardbxException("invalid log position, channel size:" + size + ", start pos:" + pos);
        } else if (size == pos) {
            log.info("channel size is equal to start pos:{}", pos);
            return;
        }

        byte[] data = new byte[512];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        final long startPos = pos < 4 ? 4 : pos;
        channel.read(buffer, startPos);
        buffer.flip();
        ByteArray ba = new ByteArray(data);
        long timestamp = ba.readLong(4);
        int eventType = ba.read();
        ba.skip(4);
        long eventSize = ba.readLong(4);
        long endPos = ba.readLong(4);

        if (timestamp < 0) {
            throw new PolardbxException("invalid event timestamp:" + timestamp);
        }
        if (eventType < 0 || eventType > 0x23) {
            throw new PolardbxException("invalid event type:" + eventType);
        }
        if (eventSize != endPos - startPos) {
            throw new PolardbxException(
                "invalid event size, start pos:" + startPos + ", end pos:" + endPos + ", event size:" + eventSize);
        }
        // todo @yudong 这个position看起来也是多余的
        // channel.position(0);
    }

    public void formatEvent() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        channel.read(buffer, 4);
        buffer.flip();
        buffer.mark();
        buffer.position(9);//go to length
        int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
            | ((buffer.get()) << 24);
        byte[] data = new byte[length];
        buffer.reset();
        buffer.get(data);
        String info = getEventInfo(data, length);
        log.info("FormatEvent {}", info);
    }

    /**
     * 如果在命令中指定了from start_pos，则需要跳过一些字节，从start_pos开始读取文件内容
     */
    public void skipPos() throws IOException {
        if (pos > 4) {
            formatEvent();
            this.fp = pos;
        } else {
            if (offset > 0) {
                formatEvent();
            }
            this.fp = 4;
        }

        channel.position(this.fp);
        read();
    }

    public void skipOffset() throws IOException {
        int count = 0;
        while (offset > count++ && hasNext()) {
            if (buffer.remaining() < 13) {
                buffer.compact();
                this.read();
            }
            int cur = buffer.position();
            buffer.position(cur + 9);// timestamp + event type + server id
            int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
                | ((buffer.get()) << 24);// event-size
            if (buffer.remaining() < length - 13) {
                channel.position(fp + length);
                buffer.position(buffer.limit());
            } else {
                buffer.position(cur + length);
            }
            fp += length;
        }
    }

    public BinlogEvent nextBinlogEvent() throws IOException {
        if (buffer.remaining() < 13) {
            buffer.compact();
            this.read();
        }
        int cur = buffer.position();
        skipBytes(4);// timestamp
        byte eventType = buffer.get();// event type
        skipBytes(4);// server id
        int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
            | ((buffer.get()) << 24);// length

        byte[] data = new byte[length];
        if (buffer.remaining() < length - 13) {
            channel.position(fp);
            channel.read(ByteBuffer.wrap(data));
            buffer.position(buffer.limit());
        } else {
            buffer.position(cur);
            buffer.get(data);
        }

        ByteArray ba = new ByteArray(data);
        ba.skip(5);
        long serverId = ba.readLong(4);
        ba.skip(4);
        long endLogPos = ba.readLong(4);
        String info = getEventInfo(data, length);
        BinlogEvent binlogEvent = BinlogEvent.newBuilder().setLogName(fileName).setPos(fp).setEventType(
            LogEvent.getTypeName(eventType)).setServerId(serverId).setEndLogPos(endLogPos).setInfo(info).build();
        fp += length;
        rowCount--;

        return binlogEvent;
    }

    private String getEventInfo(byte[] data, int length) throws IOException {

        LogEvent event = decoder.decode(new LogBuffer(data, 0, length), context);
        return StringUtils.defaultString(event.info(), StringUtils.EMPTY);
    }

    private void read() throws IOException {
        if (channel.position() == 0) {
            fp = 4;
            channel.position(4);
        }
        if (log.isDebugEnabled()) {
            log.debug("will read from {}#{}, fp={}, buffer={}", fileName, channel.position(), fp,
                bufferMessage(buffer));
        }
        int read = channel.read(buffer);
        buffer.flip();

        if (log.isDebugEnabled()) {
            log.debug("read from {}, read={},buffer={}", fileName, read, bufferMessage(buffer));
        }
    }

    public boolean hasNext() throws IOException {
        if (rowCount <= 0) {
            return false;
        }
        return buffer.hasRemaining() || fp < channel.size();
    }

    private void skipBytes(int skip) {
        int ori = buffer.position();
        buffer.position(ori + skip);
    }

    private String bufferMessage(ByteBuffer buffer) {
        return "[" + buffer.position() + "," + buffer.limit() + "," + buffer.capacity() + "]";
    }

    public void close() {
        try {
            buffer.clear();
            channel.close();
        } catch (IOException e) {
            log.warn("{} close fail ", fileName, e);
        }
    }

}
