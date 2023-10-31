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
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将指定目录下的一组binlog文件组织成一个队列
 * 提供给LocalBinlogConnection按序消费
 *
 * @author chengjin.lyf on 2019/3/2 7:07 PM
 * @since 1.0.25
 */

@Slf4j
public class BinlogFileQueue implements IBinlogFileQueue {

    private final String directory;

    private final List<String> binlogList = Lists.newArrayList();

    public BinlogFileQueue(String directory) {
        this.directory = directory;
    }

    @Override
    public File getNextFile(File pre) {
        int curIdx = binlogList.indexOf(pre.getName());
        if (curIdx + 1 >= binlogList.size()) {
            return null;
        }
        String binlogName = binlogList.get(curIdx + 1);
        File file = new File(directory + File.separator + binlogName);
        if (!file.exists()) {
            return null;
        }
        return file;
    }

    @Override
    public File getBefore(File file) {
        int curIdx = binlogList.indexOf(file.getName());
        if (curIdx == 0) {
            return null;
        }
        String binlogName = binlogList.get(curIdx - 1);
        File binlogFile = new File(directory + File.separator + binlogName);
        if (!binlogFile.exists()) {
            return null;
        }
        return binlogFile;
    }

    @Override
    public File waitForNextFile(File pre) throws InterruptedException {
        StatisticalProxy.getInstance().heartbeat();
        while (true) {
            File nextFile = getNextFile(pre);
            if (nextFile == null) {
                Thread.sleep(500L);
                continue;
            }
            return nextFile;
        }
    }

    @Override
    public File getFirstFile() {
        try {
            if (binlogList.isEmpty()) {
                return null;
            }
            File firstFile = new File(directory + File.separator + binlogList.get(0));
            while (!firstFile.exists()) {
                log.warn("wait first file downloaded: {}, {}", binlogList.get(0),
                    directory + File.separator + binlogList.get(0));
                Thread.sleep(2000L);
            }
            return firstFile;
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public void destroy() {
        binlogList.clear();
    }

    @Override
    public List<File> listBinlogFiles() {
        List<File> files = new ArrayList<>();
        for (String fileName : binlogList) {
            File file = new File(directory + File.separator + fileName);
            if (file.exists()) {
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(File::getName));
        return files;
    }

    public void setBinlogList(List<String> binlogList) {
        this.binlogList.clear();
        for (String fileName : binlogList) {
            if (this.binlogList.contains(fileName)) {
                continue;
            }
            this.binlogList.add(fileName);
        }
    }
}
