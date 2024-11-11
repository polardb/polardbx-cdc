/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote;

import java.util.List;

public interface IRemoteManager {
    void download(String fileName, String localPath) throws Throwable;

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
