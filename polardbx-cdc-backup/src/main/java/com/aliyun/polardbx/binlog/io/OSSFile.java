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
package com.aliyun.polardbx.binlog.io;

import lombok.Data;

import java.io.File;

@Data
public class OSSFile implements Comparable<OSSFile> {

    private String binlogFile;
    private File file;
    private boolean local = false;

    public OSSFile(String binlogFile, File file) {
        this.binlogFile = binlogFile;
        this.file = file;
        this.local = true;
    }

    public OSSFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public boolean isLocal() {
        return local;
    }

    public void delete() {
        if (file == null) {
            return;
        }
        file.delete();
    }

    public long size() {
        return file.length();
    }

    @Override
    public int compareTo(OSSFile o) {
        return binlogFile.compareTo(o.binlogFile);
    }
}
