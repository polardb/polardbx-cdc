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

import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;

import java.io.IOException;
import java.util.List;

/**
 * CDC文件系统接口，文件系统用于管理binlog文件
 * 有两种文件系统:local file system和remote file system
 * 本地文件系统管理存储在本地磁盘上的binlog文件
 * 远程文件系统管理存储在远端存储上的binlog文件
 */
public interface ICdcFileSystem {
    /**
     * create a new CDCFile
     *
     * @param fileName file name
     * @return CDCFile
     */
    CdcFile create(String fileName);

    /**
     * delete the file
     *
     * @param fileName file name
     */
    boolean delete(String fileName);

    /**
     * return whether the file exist
     *
     * @param fileName file name
     * @return exist or not
     */
    boolean exist(String fileName);

    /**
     * return a CDCFile object of the file
     *
     * @param fileName file name
     * @return an object, null if the file does not exist
     */
    CdcFile get(String fileName);

    /**
     * get the file's size
     *
     * @param fileName file namae
     * @return file size, -1 if the file does not exist
     */
    long size(String fileName);

    /**
     * list all files in the file system,
     * sorted by log file sequence number
     *
     * @return file list
     */
    List<CdcFile> listFiles();

    /**
     * get the file name
     *
     * @param pureName pure file name
     * @return full path
     */
    String getName(String pureName);

    /**
     * get a channel to read the file
     *
     * @param fileName file name
     * @return a channel to read the file,
     * null if the file does not exist
     */
    BinlogFileReadChannel getReadChannel(String fileName) throws IOException;
}