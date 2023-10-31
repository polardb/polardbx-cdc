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
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.RelayStreamMetrics;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.aliyun.polardbx.relay.Message;
import com.aliyun.polardbx.relay.MetaInfo;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_READ_FILE_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayFile.HEADER_SIZE;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayFileDataReader extends RelayDataReaderBase {
    private static final int READ_BUFFER_SIZE =
        DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_READ_FILE_BUFFER_SIZE);

    private final RelayFileStoreEngine fileStoreEngine;
    private MetaInfo metaInfo;
    private FileReader fileReader;

    RelayFileDataReader(RelayFileStoreEngine fileStoreEngine, RelayStreamMetrics metrics, byte[] searchFromKey) {
        super(fileStoreEngine, metrics, searchFromKey);
        this.fileStoreEngine = fileStoreEngine;
    }

    @Override
    public LinkedList<Pair<byte[], byte[]>> getDataInternal(int maxItemSize, long maxByteSize) {
        if (metaInfo == null) {
            String tso = RelayKeyUtil.extractTsoFromKey(searchFromKey);
            metaInfo = fileStoreEngine.searchCheckpointTso(tso);
            if (metaInfo != null) {
                log.info("successfully find check point for request tso {}, meta info is {}.", tso, metaInfo);
                fileReader = createFileReader(metaInfo.getFileName(), metaInfo.getFilePos());
            }
        }
        if (fileReader == null) {
            return Lists.newLinkedList();
        } else {
            long byteSize = 0;
            LinkedList<Pair<byte[], byte[]>> list = new LinkedList<>();
            for (int i = 0; i < maxItemSize; i++) {
                tryRotate();
                try {
                    Pair<byte[], byte[]> pair = fileReader.nextMessage();
                    if (pair != null) {
                        list.add(pair);
                        byteSize += pair.getValue().length;
                        if (byteSize >= maxByteSize) {
                            break;
                        }
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    throw new PolardbxException("file read error! " + fileReader.file.getName(), e);
                }
            }
            return list;
        }
    }

    @Override
    public void close() {
        if (fileReader != null) {
            fileReader.close();
        }
    }

    private void tryRotate() {
        String currentWritingFile = fileStoreEngine.getCurrentWritingFile();
        if (fileReader.file.getName().compareTo(currentWritingFile) < 0 && fileReader.isReadEnd()) {
            String nextFileName = fileStoreEngine.getRelayFileManager().nextFileName(fileReader.file.getName());
            fileReader.close();
            fileReader = createFileReader(nextFileName, 0);
        }
    }

    private FileReader createFileReader(String fileName, long filePos) {
        return new FileReader(fileStoreEngine.getRelayFileManager().getFile(fileName), filePos);
    }

    private static class FileReader {
        File file;
        FileChannel fileChannel;
        ByteBuffer buffer;

        FileReader(File file, long pos) {
            try {
                this.file = file;
                this.fileChannel = new FileInputStream(file).getChannel();
                this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
                this.init(pos);
            } catch (Exception e) {
                throw new PolardbxException(String.format("create FileReader failed, %s:%s!", file.getName(), pos));
            }
        }

        Pair<byte[], byte[]> nextMessage() throws IOException {
            if (buffer.remaining() <= HEADER_SIZE) {
                if (fileChannel.size() - fileChannel.position() + buffer.remaining() < HEADER_SIZE) {
                    return null;
                }
                buffer.compact();
                while (true) {
                    if (fileChannel.read(buffer) > 0) {
                        buffer.flip();
                        break;
                    } else {
                        sleep();
                    }
                }
            }

            int bodySize = readBodySizeFromHeader();
            byte[] bytes;
            if (buffer.remaining() >= bodySize) {
                bytes = new byte[bodySize];
                buffer.get(bytes, 0, bytes.length);
            } else {
                if (fileChannel.size() - fileChannel.position() + buffer.remaining() < bodySize) {
                    buffer.position(buffer.position() - HEADER_SIZE);//回跳，下次重新获取size
                    return null;
                }
                bytes = new byte[bodySize];
                int length = buffer.remaining();
                buffer.get(bytes, 0, length);
                buffer.clear();
                while (length < bodySize) {
                    if (fileChannel.read(buffer) <= 0) {
                        sleep();
                        continue;
                    }
                    buffer.flip();
                    int need = bodySize - length;
                    int read = Math.min(need, buffer.remaining());
                    buffer.get(bytes, length, read);
                    length += read;
                    buffer.compact();
                }
                buffer.flip();
            }
            return buildPair(bytes);
        }

        boolean isReadEnd() {
            try {
                return !buffer.hasRemaining() && fileChannel.position() == fileChannel.size();
            } catch (IOException e) {
                throw new PolardbxException("IO error!", e);
            }
        }

        void close() {
            try {
                fileChannel.close();
            } catch (IOException e) {
            }
        }

        private Pair<byte[], byte[]> buildPair(byte[] bytes) throws InvalidProtocolBufferException {
            Message message = Message.parseFrom(bytes);
            return Pair.of(DirectByteOutput.unsafeFetch(message.getKey()),
                DirectByteOutput.unsafeFetch(message.getValue()));
        }

        private void init(long pos) throws IOException {
            fileChannel.position(pos);
            fileChannel.read(buffer);
            buffer.flip();
        }

        private int readBodySizeFromHeader() {
            int size = readInt(buffer);
            buffer.get();
            return size;
        }

        private int readInt(ByteBuffer byteBuffer) {
            int result = 0;
            for (int i = 0; i < 4; ++i) {
                result |= (read(byteBuffer) << (i << 3));
            }
            return result;
        }

        private int read(ByteBuffer byteBuffer) {
            return byteBuffer.get() & 0xff;
        }

        private void sleep() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
    }
}
