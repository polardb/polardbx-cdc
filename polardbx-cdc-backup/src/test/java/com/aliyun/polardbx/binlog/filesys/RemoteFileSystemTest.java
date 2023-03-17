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
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author yudong
 * @since 2022/8/21
 **/
public class RemoteFileSystemTest {
    private final String group = "group1";
    private final String stream = "stream1";
    RemoteFileSystem fileSystem = new RemoteFileSystem(group, stream);

    @Before
    public void before() {
        final SpringContextBootStrap appContextBootStrap =
            new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @After
    public void after() {
        String fullPath = fileSystem.getName("");
        RemoteBinlogProxy.getInstance().deleteAll(fullPath);
    }

    @Test
    public void sizeTest() {
        // todo @yudong
    }

    @Test
    public void deleteTest() {
        // todo @yudong
    }

    @Test
    public void listFilesTest() {
        // todo @yudong
    }

    @Test
    public void getChannelTest() throws IOException {
        String fileName = "get-channel-test.txt";
        fileSystem.delete(fileName);
        String content = "Let life be beautiful like summer flowers and death like autumn leaves.";
        String fullName = fileSystem.getName(fileName);
        Appender appender = RemoteBinlogProxy.getInstance().providerAppender(fullName);
        appender.begin();
        appender.append(content.getBytes(), content.length());
        appender.end();
        BinlogFileReadChannel channel = fileSystem.getReadChannel(fileName);
        byte[] data = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.read(buffer, 0);
        buffer.flip();
        String actual = new String(buffer.array(), 0, buffer.limit());
        Assert.assertEquals(content, actual);
        channel.close();
    }
}
