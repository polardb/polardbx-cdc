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

import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

@Slf4j
@Ignore
public class LogFileGeneratorTest extends BaseTest {

    private static final byte[] BINLOG_FILE_HEADER = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};
    final String version = "8.0.21-PolarDB-X";
    byte[] bytesOfEventHeader = new byte[32];
    byte[] bytesOfEventData = new byte[1024];

    File dir = new File(System.getProperty("user.dir") + "/binlog");

    List<byte[]> data = Lists.newArrayList();

    @Test
    public void testCalcSeekBufferSize() {
        int size = LogFileGenerator.calcSeekBufferSize(false, 4);
        Assert.assertEquals(32, size);

        size = LogFileGenerator.calcSeekBufferSize(true, 4);
        Assert.assertEquals(32, size);

        size = LogFileGenerator.calcSeekBufferSize(true, 32);
        Assert.assertEquals(8, size);
    }

    @Test
    public void testCalcFlowControlWindowSize() {
        int size = LogFileGenerator.calcFlowControlWindowSize(false, 0, 0);
        Assert.assertEquals(256, size);

        size = LogFileGenerator.calcFlowControlWindowSize(false, 0, 100);
        Assert.assertEquals(80, size);

        size = LogFileGenerator.calcFlowControlWindowSize(false, 0, 500);
        Assert.assertEquals(256, size);

        size = LogFileGenerator.calcFlowControlWindowSize(true, 8, 0);
        Assert.assertEquals(64, size);

        size = LogFileGenerator.calcFlowControlWindowSize(true, 8, 1000);
        Assert.assertEquals(64, size);

        size = LogFileGenerator.calcFlowControlWindowSize(true, 8, 256);
        Assert.assertEquals(25, size);
    }

    @Test
    public void read() {
        System.out.println(System.getProperty("user.dir"));
        long pos = 4;
        try (RandomAccessFile file = new RandomAccessFile(dir + "/mysql/binlog.000001", "r")) {
            file.seek(pos);

            for (; ; ) {
                int read = file.read(bytesOfEventHeader, 0, 19);
                if (read < 0) {
                    break;
                }
                final ByteArray byteArray = new ByteArray(bytesOfEventHeader);
                byteArray.skip(13);
                long endPos = byteArray.readLong(4);

                final int dataSize = (int) (endPos - pos - 19);
                log.info("pos={}, endPos={}, dataLength={}", pos, endPos, dataSize);
                file.read(bytesOfEventData, 0, dataSize);
                byte[] bytesOfEvent = new byte[dataSize + 19];
                System.arraycopy(bytesOfEventHeader, 0, bytesOfEvent, 0, 19);
                System.arraycopy(bytesOfEventData, 0, bytesOfEvent, 19, dataSize);
                data.add(bytesOfEvent);
                pos = endPos;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void write() {
        read();

        //data 混淆打乱（排除Format_desc，和Previous_gtids）
        //Previous_gtid_log_event 在每个binlog 头部都会有
        //每次binlog rotate的时候存储在binlog头部
        //Previous-GTIDs在binlog中只会存储在这台机器上执行过的所有binlog，不包括手动设置gtid_purged值。
        //换句话说，如果你手动set global gtid_purged=xx； 那么xx是不会记录在Previous_gtid_log_event中的。

        Collections.shuffle(data.subList(2, data.size()));

        try (RandomAccessFile file = new RandomAccessFile(dir + "/polardbx/binlog.000001", "rw")) {
            file.write(BINLOG_FILE_HEADER);
            final ByteArray ba = new ByteArray(data.get(0));
            ba.skip(19 + 2);
            ba.writeString(version);
            file.write(data.get(0));
            file.write(data.get(1));

            long length = BINLOG_FILE_HEADER.length + data.get(0).length + data.get(1).length;

            for (int i = 2; i < data.size(); i++) {
                byte[] bytes = data.get(i);
                ByteArray byteArray = new ByteArray(bytes);
                byteArray.skip(13);
                long oldEndPos = byteArray.readLong(4);
                length += bytes.length;
                byteArray.reset();
                byteArray.skip(13);
                byteArray.writeLong(length, 4);

                byteArray.reset();
                byteArray.skip(13);
                long newEndPos = byteArray.readLong(4);
                log.info("oldEndPos={}, newEndPos={}", oldEndPos, newEndPos);

                file.write(bytes);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 在binlog文件头部添加一个USER_VAR_EVENT，记录当前处理的TSO以及binlog位点（便于故障恢复时快速定位回溯位置）
     */

    @Test
    public void testAppendTSOAndBegin() {
        /*
         * new File(dir + "/polardbx/binlog.000003").delete(); final List<byte[]> read =
         * BinlogReader.read(dir + "/mysql/binlog.000000"); final String tso_id =
         * "1234567812345678.12345678"; try (RandomAccessFile file = new
         * RandomAccessFile(dir + "/polardbx/binlog.000003", "rw")) { final int
         * headerLength = BINLOG_FILE_HEADER.length + read.get(0).length +
         * read.get(1).length; long pos = 0; file.write(BINLOG_FILE_HEADER); pos +=
         * BINLOG_FILE_HEADER.length; file.write(read.get(0)); pos +=
         * read.get(0).length; file.write(read.get(1)); pos += read.get(1).length; final
         * Pair<byte[], Integer> tso = makeTSO(tso_id, pos, read.get(0));
         * file.write(tso.getLeft(), 0, tso.getRight()); pos += tso.getRight(); final
         * Pair<byte[], Integer> begin = makeBegin(System.currentTimeMillis() / 1000,
         * "d1", pos, read.get(0)); file.write(begin.getLeft(), 0, begin.getRight());
         * pos += begin.getRight(); final Pair<byte[], Integer> commit =
         * makeCommit(System.currentTimeMillis() / 1000, 1001, pos, read.get(0));
         * file.write(commit.getLeft(), 0, commit.getRight()); pos += commit.getRight();
         * updateTSO(file, tso_id, pos, headerLength); final Pair<String, Integer> data
         * = getTSO(file, headerLength); Assert.assertEquals(tso_id, data.getLeft());
         * Assert.assertEquals(Long.valueOf(pos), Long.valueOf(data.getRight())); }
         * catch (IOException e) { e.printStackTrace(); }
         */
    }

    @Test
    public void testCrc32() {
        byte[] d = {
            0x1d, (byte) 0x87, (byte) 0xc7, (byte) 0x5f, (byte) 0x13, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x2e, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xcd, (byte) 0x02, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x51, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x01,
            (byte) 0x00, (byte) 0x02, (byte) 0x64, (byte) 0x31, (byte) 0x00, (byte) 0x02, (byte) 0x74, (byte) 0x31,
            (byte) 0x00,
            (byte) 0x01, (byte) 0x03, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x9c,
            (byte) 0xaa,
            (byte) 0x5a, (byte) 0x53};
        CRC32 crc32 = new CRC32();
        crc32.update(d, 0, d.length - 4);
        final ByteArray ba = new ByteArray(d);
        ba.skip(d.length - 4);
        System.out.println(ba.readLong(4));
        System.out.println(crc32.getValue());
        ba.reset();
        System.out.println(ba.readInteger(4));
        System.out.println(ba.read());
        System.out.println(ba.readInteger(4));
    }

    @Test
    public void testFile() {
        try (RandomAccessFile file = new RandomAccessFile(dir + "/file/t.txt", "rw")) {
            int i = 0;
            Stopwatch stopwatch = Stopwatch.createStarted();
            while (i++ < 3000000) {
                file.writeBytes(
                    "HelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHello");
            }
            stopwatch.stop();
            System.out.println(stopwatch);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testReadFile() {
        byte[] data = new byte[1024];
        try (RandomAccessFile file = new RandomAccessFile(dir + "/file/t.txt", "r")) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            long b = 0;
            int read;
            while ((read = file.read(data)) > 0) {
                //
                b += read;
            }
            System.out.println(b);
            stopwatch.stop();
            System.out.println(stopwatch);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMappedByteBufferReadFile() {
        try (RandomAccessFile file = new RandomAccessFile(dir + "/file/t.txt", "r")) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            MappedByteBuffer out = file.getChannel().map(MapMode.READ_ONLY, 0, 128);

            log.info("capacity {} {}", out.capacity(), out.limit());
            stopwatch.stop();
            System.out.println(stopwatch);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testByteBufferReadFile() {
        try (FileInputStream inputStream = new FileInputStream(dir + "/file/t.txt")) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            FileChannel channel = inputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            long b = 0;
            int read;
            while ((read = channel.read(buffer)) > 0) {
                b += read;
                buffer.flip();
            }
            System.out.println(b);
            stopwatch.stop();
            System.out.println(stopwatch);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTableMap() {
        byte[] d = {
            //(byte)0x1d, (byte)0x87, (byte)0xc7, (byte)0x5f, 0x13, 01, 00, 00, 00, 0x2e, 00, 00, 00, (byte)0xcd, 02,
            // 00, 00, 00, 00, //header
            (byte) 0x51, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,//table id
            (byte) 0x01, (byte) 0x00,//flags
            (byte) 0x02,//schema name length
            (byte) 0x64, (byte) 0x31,//schema name
            (byte) 0x00,//[00]
            (byte) 0x02,//table name length
            (byte) 0x74, (byte) 0x31, //table name
            (byte) 0x00,//[00]
            (byte) 0x01,//column-count
            (byte) 0x03, //column-def
            (byte) 0x00, //col meta length
            //col meta (null)
            (byte) 0x01, //NULL-bitmask, length: (column-count + 8) / 7
            (byte) 0x01, (byte) 0x01, (byte) 0x00,
            (byte) 0x9c, (byte) 0xaa, (byte) 0x5a, (byte) 0x53 //check sum
        };

        d = new byte[] {
            (byte) 0xd4, (byte) 0x04, (byte) 0x23, (byte) 0x48, (byte) 0x59, (byte) 0x30, (byte) 0x30, (byte) 0x30,
            (byte) 0x43,
            (byte) 0x6f, (byte) 0x75, (byte) 0x6c, (byte) 0x64, (byte) 0x20, (byte) 0x6e, (byte) 0x6f, (byte) 0x74,
            (byte) 0x20,
            (byte) 0x66, (byte) 0x69, (byte) 0x6e, (byte) 0x64, (byte) 0x20, (byte) 0x66, (byte) 0x69, (byte) 0x72,
            (byte) 0x73,
            (byte) 0x74, (byte) 0x20, (byte) 0x6c, (byte) 0x6f, (byte) 0x67, (byte) 0x20, (byte) 0x66, (byte) 0x69,
            (byte) 0x6c,
            (byte) 0x65, (byte) 0x20, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x20, (byte) 0x69,
            (byte) 0x6e,
            (byte) 0x20, (byte) 0x62, (byte) 0x69, (byte) 0x6e, (byte) 0x61, (byte) 0x72, (byte) 0x79, (byte) 0x20,
            (byte) 0x6c,
            (byte) 0x6f, (byte) 0x67, (byte) 0x20, (byte) 0x69, (byte) 0x6e, (byte) 0x64, (byte) 0x65, (byte) 0x78,
            (byte) 0x20,
            (byte) 0x66, (byte) 0x69, (byte) 0x6c, (byte) 0x65
        };

        final ByteArray ba = new ByteArray(d);

        System.out.println(ba.readLong(2));
        System.out.println(ba.readEofString(false));

        //ba.reset();
        //System.out.println(ba.readInteger(6));
        //System.out.println(ba.readInteger(2));
        //int schemaLen = ba.readInteger(1);
        //System.out.println(schemaLen);
        //System.out.println(ba.readString(schemaLen));
        //System.out.println(ba.read());
        //int tableNameLen = ba.read();
        //System.out.println(ba.readString(tableNameLen));
        //System.out.println(ba.read());
        //
        //final long column_count = ba.readLenenc();
        //System.out.println("column-count=" + column_count);
        //ba.skip((int)column_count);
        ////column_meta_def (lenenc_str)
        //final long meta_def_len = ba.readLenenc();
        //System.out.println(meta_def_len);
        //System.out.println(ba.readString((int)meta_def_len));
        //
        ////System.out.println(Arrays.toString("id".getBytes()));
        ////System.out.println(HexUtil.format(HexUtil.encodeHexStr("id")));
        ////System.out.println(HexUtil.format(HexUtil.encodeHexStr("name")));
        //
        //byte[] m = new byte[8];
        //final ByteArray mock = new ByteArray(m);
        //mock.writeLong(256, 4);
        //System.out.println(Arrays.toString(m));
        //System.out.println(HexUtil.encodeHex(m));
        //
        //int x = (0x04 << 8); // real_type
        //x += 0x03; // pack or field length
        //System.out.println(x);

    }

    @Test
    public void test() {
        byte[] m = new byte[8];
        final ByteArray mock = new ByteArray(m);
        mock.writeLong(250, 4);

        System.out.println(Arrays.toString(m));
        mock.reset();

        mock.writeLong(256, 4);
        System.out.println(Arrays.toString(m));
    }
}
