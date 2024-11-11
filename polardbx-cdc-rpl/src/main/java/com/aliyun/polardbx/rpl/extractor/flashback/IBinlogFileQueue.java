/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import java.io.File;
import java.util.List;

/**
 * @author ziyang.lb
 */
public interface IBinlogFileQueue {

    /**
     * Get the file after pre in the queue.
     */
    File getNextFile(File pre);

    /**
     * Get the file before pre in the queue.
     */
    File getBefore(File file);

    /**
     * Wait until the file after pre in the queue is ready, then return the file.
     */
    File waitForNextFile(File pre) throws InterruptedException;

    /**
     * Return the first file in the queue.
     */
    File getFirstFile();

    /**
     * Destroy the queue.
     */
    void destroy();

    /**
     * List all the binlog files in the directory.
     */
    List<File> listBinlogFiles();
}
