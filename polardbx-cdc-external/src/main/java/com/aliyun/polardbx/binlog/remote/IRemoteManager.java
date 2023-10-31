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
package com.aliyun.polardbx.binlog.remote;

import java.util.List;

public interface IRemoteManager {
    void download(String fileName, String localPath);

    String getMd5(String fileName);

    long getSize(String fileName);

    void delete(String fileName);

    void deleteAll(String prefix);

    boolean isObjectsExistForPrefix(String pathPrefix);

    List<String> listFiles(String path);

    byte[] getObjectData(String fileName);

    Appender providerMultiAppender(String fileName, long fileLength);

    Appender providerAppender(String fileName);

    boolean supportMultiAppend();

    boolean useMultiAppender(long size);

    String prepareDownloadLink(String fileName, long expireTimeInSec);

    List<String> listBuckets();
}
