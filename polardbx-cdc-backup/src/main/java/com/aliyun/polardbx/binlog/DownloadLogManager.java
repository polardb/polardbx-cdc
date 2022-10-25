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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DownloadLogManager {

    private static final String DOWNLOAD_FILE_LOG = "download.log";
    private static final Logger logger = LoggerFactory.getLogger(DownloadLogManager.class);

    private static final DownloadLogManager instance = new DownloadLogManager();
    private static final String SUCCESS_FLAG = "success";
    private static final String DOWNLOADING_FLAG = "downloading";
    private final LinkedHashMap<String, String> downloadStateMap = new LinkedHashMap<>();
    private final String fileDir;

    private DownloadLogManager() {
        fileDir = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DOWNLOAD_DATA_FILE_DIR) + File.separator
            + DOWNLOAD_FILE_LOG;
        try {
            File dir = new File(DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DOWNLOAD_DATA_FILE_DIR));
            if (!dir.exists()) {
                dir.mkdirs();
            }
            loadAll();
        } catch (IOException e) {
            logger.error("load download log file failed!", e);
            throw new PolardbxException(e);
        }

    }

    public static DownloadLogManager getInstance() {
        return instance;
    }

    public List<String> getDownloadList() {
        return downloadStateMap.entrySet().stream().filter(e -> StringUtils.equals(e.getValue(), DOWNLOADING_FLAG))
            .map(Map.Entry::getKey).sorted(Comparator.reverseOrder())
            .collect(
                Collectors.toList());
    }

    public synchronized void successDownload(String name) {
        downloadStateMap.put(name, SUCCESS_FLAG);
        append(name, SUCCESS_FLAG);
    }

    public boolean hasSuccessFile() {
        for (Map.Entry<String, String> entry : downloadStateMap.entrySet()) {
            if (StringUtils.equals(entry.getValue(), SUCCESS_FLAG)) {
                return true;
            }
        }
        return false;
    }

    public void addDownloadList(String name) {
        downloadStateMap.put(name, DOWNLOADING_FLAG);
    }

    public void clean() {
        File localFile = new File(fileDir);
        if (localFile.exists()) {
            localFile.delete();
        }
        downloadStateMap.clear();
    }

    public void loadAll() throws IOException {
        File localFile = new File(fileDir);
        if (!localFile.exists()) {
            return;
        }
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(new FileInputStream(localFile)));
        try {
            String line = null;
            final int nameIndex = 0;
            final int resultIndex = 1;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                String[] ds = line.split(" ");
                if (ds.length == 0) {
                    continue;
                }
                if (ds.length == 1) {
                    downloadStateMap.put(ds[nameIndex], DOWNLOADING_FLAG);
                } else {
                    downloadStateMap.put(ds[nameIndex], ds[resultIndex]);
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

    }

    private boolean allOk() {
        for (Map.Entry<String, String> entry : downloadStateMap.entrySet()) {
            if (!StringUtils.equals(entry.getValue(), SUCCESS_FLAG)) {
                return false;
            }
        }
        return true;
    }

    public void buildBaseLog() {
        BufferedWriter writer = null;
        try {
            writer =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileDir))));
            for (Map.Entry<String, String> entry : downloadStateMap.entrySet()) {
                writer.append(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        } catch (Exception e) {
            logger.error("write download.log file failed!", e);
            throw new PolardbxException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void append(String name, String result) {
        if (allOk()) {
            clean();
            return;
        }
        BufferedWriter writer = null;
        try {
            writer =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileDir), true)));
            writer.newLine();
            writer.append(name + " " + result + "  change@" + DateFormatUtils
                .format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss.SSS"));
        } catch (Exception e) {
            logger.error("write download.log file failed!", e);
            throw new PolardbxException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

}
