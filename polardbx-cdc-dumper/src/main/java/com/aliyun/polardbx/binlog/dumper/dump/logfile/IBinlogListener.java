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

import java.io.File;

/**
 * binlog文件监听器
 *
 * @author chengjin
 */
public interface IBinlogListener {
    /**
     * 当binlog文件创建时，调用此方法
     *
     * @param file binlog文件
     */
    void onCreateFile(File file);

    /**
     * 当binlog文件发生rotate时，调用此方法
     *
     * @param currentFile 当前binlog文件
     * @param nextFile 下一个binlog文件
     */
    void onRotateFile(File currentFile, String nextFile);

    /**
     * 当binlog文件结束时，调用此方法
     *
     * @param file binlog文件
     * @param binlogEndInfo binlog文件结束信息
     */
    void onFinishFile(File file, BinlogEndInfo binlogEndInfo);

    /**
     * 当binlog文件被删除时，调用此方法
     *
     * @param file binlog文件
     */
    void onDeleteFile(File file);
}
