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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author yudong
 * @since 2023/1/12 10:58
 **/
@Slf4j
public class BinlogFileUtil {
    /**
     * binlog文件名前缀
     */
    public static final String BINLOG_FILE_PREFIX = "binlog";
    /**
     * binlog文件名前缀和后缀的分隔符，必须保证前缀和后缀中都没有该字符
     */
    public static final String BINLOG_FILE_NAME_SEPARATOR = ".";
    /**
     * binlog文件名后缀数字串的长度
     */
    public static final int BINLOG_FILE_NAME_SEQUENCE_LEN = 6;
    /**
     * 第一个binlog文件的序号
     */
    public static final int BINLOG_FILE_NAME_START_SEQUENCE = 1;
    /**
     * binlog文件名的最大序号
     */
    public static final int BINLOG_FILE_NAME_MAX_SEQUENCE = 999999;
    /**
     * binlog文件后缀格式
     */
    public static final String BINLOG_FILE_NAME_SUFFIX_FORMAT = "%0" + BINLOG_FILE_NAME_SEQUENCE_LEN + "d";
    /**
     * pattern，用于判定一个本地文件是否是binlog文件
     */
    public static final String BINLOG_FILE_NAME_PATTERN_FORMAT =
        "^%s\\.(?!0{" + BINLOG_FILE_NAME_SEQUENCE_LEN + "})\\d{" + BINLOG_FILE_NAME_SEQUENCE_LEN + "}$";
    /**
     * pattern，用于判定一个文件是否是binlog文件
     */
    public static final String BINLOG_FILE_NAME_PATTERN =
        "^\\w+\\.(?!0{" + BINLOG_FILE_NAME_SEQUENCE_LEN + "})\\d{" + BINLOG_FILE_NAME_SEQUENCE_LEN + "}$";

    // ============== binlog file name related method ==============

    /**
     * 获得本地binlog文件的文件名前缀，不带'.'
     */
    public static String getBinlogFilePrefix(String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return BINLOG_FILE_PREFIX;
        } else {
            // aws s3协议不支持'#'字符
            return streamName + "_" + BINLOG_FILE_PREFIX;
        }
    }

    /**
     * 给定binlog文件的前缀和文件名，判断该文件是否是一个binlog文件
     */
    public static boolean isBinlogFile(String fileName, String prefix) {
        if (fileName == null || prefix == null) {
            return false;
        }
        String pattern = String.format(BINLOG_FILE_NAME_PATTERN_FORMAT, prefix);
        return Pattern.matches(pattern, fileName);
    }

    public static boolean isBinlogFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        return Pattern.matches(BINLOG_FILE_NAME_PATTERN, fileName);
    }

    /**
     * 获得第一个binlog文件的文件名
     */
    public static String getFirstBinlogFileName(String groupName, String streamName) {
        String prefix = getBinlogFilePrefix(groupName, streamName);
        return prefix + BINLOG_FILE_NAME_SEPARATOR + String.format(BINLOG_FILE_NAME_SUFFIX_FORMAT, 1);
    }

    /**
     * 给定一个binlog文件的文件名，获得该binlog文件的下一个文件的文件名
     */
    public static String getNextBinlogFileName(String fileName) {
        Pair<String, Integer> pair = splitBinlogFileName(fileName);
        int currentSequence = pair.getRight();
        if (currentSequence == BINLOG_FILE_NAME_MAX_SEQUENCE) {
            log.warn("binlog file seq has reached to max, will start from 1");
            currentSequence = 0;
        }
        String newSuffix = String.format(BINLOG_FILE_NAME_SUFFIX_FORMAT, ++currentSequence);
        return pair.getLeft() + BINLOG_FILE_NAME_SEPARATOR + newSuffix;
    }

    public static String getPrevBinlogFileName(String fileName) {
        Pair<String, Integer> pair = splitBinlogFileName(fileName);
        int currentSequence = pair.getRight();
        if (currentSequence == BINLOG_FILE_NAME_START_SEQUENCE) {
            return null;
        }
        String newSuffix = String.format(BINLOG_FILE_NAME_SUFFIX_FORMAT, --currentSequence);
        return pair.getLeft() + BINLOG_FILE_NAME_SEPARATOR + newSuffix;
    }

    private static Pair<String, Integer> splitBinlogFileName(String fileName) {
        String[] splits = fileName.split("\\.");
        String prefix = splits[0];
        String suffix = splits[1];
        return Pair.of(prefix, Integer.parseInt(suffix));
    }

    /**
     * 获得指定序号的binlog文件的文件名
     */
    public static String getBinlogFileNameBySequence(String prefix, int seq) {
        if (seq <= 0 || BINLOG_FILE_NAME_MAX_SEQUENCE < seq) {
            throw new PolardbxException("invalid binlog file name sequence " + seq);
        }
        String suffix = String.format(BINLOG_FILE_NAME_SUFFIX_FORMAT, seq);
        return prefix + BINLOG_FILE_NAME_SEPARATOR + suffix;
    }

    /**
     * 获得指定序号的binlog文件的文件名
     */
    public static String getBinlogFileNameBySequence(String groupName, String streamName, int seq) {
        if (seq <= 0 || BINLOG_FILE_NAME_MAX_SEQUENCE < seq) {
            throw new PolardbxException("invalid binlog file name sequence " + seq);
        }
        String prefix = getBinlogFilePrefix(groupName, streamName);
        String suffix = String.format(BINLOG_FILE_NAME_SUFFIX_FORMAT, seq);
        return prefix + BINLOG_FILE_NAME_SEPARATOR + suffix;
    }

    // ============== binlog file path related method ==============

    /**
     * 构造本地binlog文件存储的根路径，不带最后的'/'
     * 单流的根路径就是binlog文件存储的全路径
     * 多流的根路径下面还需要区分不同group和stream
     *
     * @param version 多流版本号，从taskConfig表中获取，由Daemon构造并写入metaDB
     */
    public static String getBinlogFileRootPath(TaskType taskType, long version) {
        if (taskType == TaskType.Dumper) {
            return DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH);
        } else if (taskType == TaskType.DumperX) {
            String versionPath = Constants.VERSION_PATH_PREFIX + version;
            return DynamicApplicationConfig.getString(ConfigKeys.BINLOG_X_DIR_PATH_PREFIX) + File.separator
                + versionPath;
        } else {
            throw new PolardbxException("invalid task type " + taskType);
        }
    }

    /**
     * 构造本地binlog文件存储的全路径，该目录下面存放的就是binlog文件
     * 注意，路径最后不带'/'
     */
    public static String getBinlogFileFullPath(String rootPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return rootPath;
        }
        return rootPath + File.separator + groupName + File.separator + streamName;
    }

    public static String getBinlogFileFullPath(String groupName, String streamName, long version) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return getBinlogFileRootPath(TaskType.Dumper, -1);
        } else {
            return getBinlogFileRootPath(TaskType.DumperX, version) + File.separator + groupName + File.separator
                + streamName;
        }
    }

    public static String extractRootPathFromFullPath(String fullPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return getBinlogFileRootPath(TaskType.Dumper, -1);
        }
        return fullPath.substring(0, fullPath.indexOf(groupName) - 1);
    }

    /**
     * 判断全路径是否正确
     */
    public static boolean isValidFullPath(String fullPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return fullPath.equals(getBinlogFileRootPath(TaskType.Dumper, -1));
        } else {
            // 确保多流全路径区分了不同的group和stream
            String[] res = fullPath.split(File.separator);
            if (res.length < 2) {
                return false;
            }
            int len = res.length;
            return res[len - 1].equals(streamName) && res[len - 2].equals(groupName);
        }
    }

    // ============== binlog file operation related method ==============

    public static File[] listBinlogFiles(String binlogFullPath, String groupName, String streamName) {
        String prefix = getBinlogFilePrefix(groupName, streamName);
        return new File(binlogFullPath).listFiles(((dir, name) -> isBinlogFile(name, prefix)));
    }

    public static void deleteBinlogFiles(String binlogFullPath) throws IOException {
        FileUtils.cleanDirectory(new File(binlogFullPath));
    }

    public static String buildRemoteFilePartName(String fileName, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return fileName;
        } else {
            return String.format("%s/%s/%s", groupName, streamName, fileName);
        }
    }

    public static String buildRemoteFileFullName(String partName, String instId) {
        return String.format("polardbx_cdc/%s/%s", instId, partName);
    }
}
