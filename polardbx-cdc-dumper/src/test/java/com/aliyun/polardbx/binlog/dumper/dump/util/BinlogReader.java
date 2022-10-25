/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 *
 */
@Slf4j
public class BinlogReader {
    private static final byte[] HEADER = new byte[19];

    public static List<byte[]> read(String fileName) {
        long pos = 4;
        List<byte[]> data = Lists.newArrayList();
        try (RandomAccessFile file = new RandomAccessFile(fileName, "r")) {
            file.seek(pos);

            for (; ; ) {
                int read = file.read(HEADER, 0, 19);
                if (read < 0) {
                    break;
                }
                final ByteArray byteArray = new ByteArray(HEADER);
                byteArray.skip(13);
                long endPos = byteArray.readLong(4);

                final int dataSize = (int) (endPos - pos - 19);
                file.seek(pos);
                byte[] event = new byte[(int) (endPos - pos)];
                file.read(event, 0, (int) (endPos - pos));
                data.add(event);
                pos = endPos;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    @Test
    public void t1() {
        byte[] data = new byte[] {
            0x04, 0x00, 0x38, 0x2e, 0x30, 0x2e, 0x32, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x13, 0x00, 0x0d, 0x00, 0x08, 0x00, 0x00, 0x00
            , 0x00, 0x04, 0x00, 0x04, 0x00, 0x00, 0x00, 0x61, 0x00, 0x04, 0x1a, 0x08, 0x00, 0x00, 0x00, 0x08
            , 0x08, 0x08, 0x02, 0x00, 0x00, 0x00, 0x0a, 0x0a, 0x0a, 0x2a, 0x2a, 0x00, 0x12, 0x34, 0x00, 0x0a
            , 0x28, 0x01, 0x26, (byte) 0xfe, (byte) 0xee, 0x15};
        ByteArray byteArray = new ByteArray(data);
        System.out.println(byteArray.readInteger(2));
        System.out.println(byteArray.readString(50).trim());
        System.out.println(byteArray.readLong(4));
        System.out.println(byteArray.read());

    }

    @Test
    public void t2() {
        byte[] data = new byte[] {
            (byte) 0xf2, (byte) 0x86, (byte) 0xc7, (byte) 0x5f, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x66, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x4f, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            (byte) 0x08,
            (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x2d, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00, (byte) 0x01, (byte) 0x20, (byte) 0x00, (byte) 0xa0, (byte) 0x45, (byte) 0x00, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00, (byte) 0x06, (byte) 0x03, (byte) 0x73, (byte) 0x74, (byte) 0x64, (byte) 0x04, (byte) 0x21,
            (byte) 0x00,
            (byte) 0x21, (byte) 0x00, (byte) 0xff, (byte) 0x00, (byte) 0x0c, (byte) 0x01, (byte) 0x64, (byte) 0x31,
            (byte) 0x00,
            (byte) 0x11, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00,
            (byte) 0x12, (byte) 0xff, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x64, (byte) 0x31, (byte) 0x00,
            (byte) 0x63,
            (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x20, (byte) 0x64, (byte) 0x61,
            (byte) 0x74,
            (byte) 0x61, (byte) 0x62, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x20, (byte) 0x64, (byte) 0x31,
            (byte) 0xe1,
            (byte) 0xc3, (byte) 0xc0, (byte) 0x04
        };
        ByteArray byteArray = new ByteArray(data);
        System.out.println(byteArray.readLong(4));
        System.out.println(byteArray.read());
        byteArray.skip(14);
        System.out.println(byteArray.readLong(4));
        System.out.println(byteArray.readLong(4));
        System.out.println(byteArray.read());
        byteArray.skip(2);

        final int varLen = byteArray.readInteger(2);
        System.out.println("varLen=" + varLen);
        System.out.println(byteArray.readString(varLen));
        System.out.println(byteArray.readString(2));
        System.out.println(byteArray.read());
        System.out.println(byteArray.readEofString(true));

    }

    @Test
    public void t3() {
        String s
            =
            "000000000f84f2f39b770000007b00000000000400352e362e32392d5444444c2d352e342e392d31363135353630300000000000000000000000000000000000000000000000000000000013000d0008000000000400040000005f00041a08000000080808020000000a0a0a2a2a00123400011c98871e";
        final byte[] bytes = bytesToArray(s);

        System.out.println(Arrays.toString(bytes));
        ByteArray ba = new ByteArray(bytes);
        ba.skip(bytes.length - 4);
        System.out.println(ba.readLong(4));
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length - 4);

        System.out.println(crc32.getValue());
        ba.reset();
        ba.skip(bytes.length - 4);
        ba.writeLong(crc32.getValue(), 4);
        System.out.println(Arrays.toString(bytes));

        showEvent(ByteString.copyFrom(bytes));
    }

    private byte[] bytesToArray(String bytes) {
        byte[] result = new byte[bytes.length() / 2];
        for (int i = 0; i < bytes.length() / 2; i++) {
            String temp = bytes.substring(i * 2, (i + 1) * 2);
            byte v = (byte) Integer.parseInt(temp, 16);
            result[i] = v;
        }
        return result;
    }

    private void show(ByteString pack) {
        byte[] data = pack.toByteArray();
        ByteArray ba = new ByteArray(data);
        while (ba.getPos() < ba.getLimit()) {
            ba.skip(3);
            final int seq = ba.read();
            ba.skip(5);
            int eventType = ba.read();
            long serverId = ba.readLong(4);
            long eventSize = ba.readLong(4);
            int endPos = ba.readInteger(4);
            log.info("{}: serverId={} payload {}[{}->{}]", seq, serverId,
                LogEvent.getTypeName(eventType), endPos - eventSize, endPos);
            ba.skip((int) (eventSize - 17));
        }
    }

    private void showEvent(ByteString pack) {
        byte[] data = pack.toByteArray();
        ByteArray ba = new ByteArray(data);
        while (ba.getPos() < ba.getLimit()) {

            ba.skip(4);
            int eventType = ba.read();
            long serverId = ba.readInteger(4);
            long eventSize = ba.readLong(4);
            int endPos = ba.readInteger(4);
            log.info("serverId={} payload {}[{}->{}]", serverId,
                LogEvent.getTypeName(eventType), endPos - eventSize, endPos);
            ba.skip((int) (eventSize - 17));
        }
    }

    @Test
    public void t4() {
        byte[] data = new byte[4];
        ByteArray ba = new ByteArray(data);
        ba.writeLong(2616455812L, 4);
        System.out.println(Arrays.toString(data));
        ba.reset();
        System.out.println(ba.readInteger(4));
        ba.reset();
        System.out.println(ba.readLong(4));

        ba.reset();
        ba.writeLong(-1678511484, 4);
        ba.reset();
        System.out.println(ba.readLong(4));

    }
}
