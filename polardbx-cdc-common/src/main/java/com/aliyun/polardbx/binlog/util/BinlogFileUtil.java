/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    public static String extractStreamName(String fileName) {
        if (StringUtils.isEmpty(fileName)) {
            // show binlog events 默认输出单流binlog
            return CommonConstants.STREAM_NAME_GLOBAL;
        }
        if (!isBinlogFile(fileName)) {
            throw new IllegalArgumentException("invalid binlog file name " + fileName);
        }
        int idx = fileName.lastIndexOf('_');
        if (idx == -1) {
            return CommonConstants.STREAM_NAME_GLOBAL;
        } else {
            return fileName.substring(0, idx);
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
    public static String getRootPath(TaskType taskType, long version) {
        if (taskType == TaskType.Dumper) {
            return DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH);
        } else if (taskType == TaskType.DumperX) {
            String versionPath = CommonConstants.VERSION_PATH_PREFIX + version;
            return DynamicApplicationConfig.getString(ConfigKeys.BINLOGX_DIR_PATH_PREFIX) + File.separator
                + versionPath;
        } else {
            throw new PolardbxException("invalid task type " + taskType);
        }
    }

    /**
     * 构造本地binlog文件存储的全路径，该目录下面存放的就是binlog文件
     * 注意，路径最后不带'/'
     */
    public static String getFullPath(String rootPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return rootPath;
        }
        return rootPath + File.separator + groupName + File.separator + streamName;
    }

    public static String getFullPath(String groupName, String streamName, long version) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return getRootPath(TaskType.Dumper, -1);
        } else {
            return getRootPath(TaskType.DumperX, version) + File.separator + groupName + File.separator
                + streamName;
        }
    }

    public static String extractRootPathFromFullPath(String fullPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return getRootPath(TaskType.Dumper, -1);
        }
        return fullPath.substring(0, fullPath.indexOf("/" + groupName + "/" + streamName));
    }

    /**
     * 判断全路径是否正确
     */
    public static boolean isValidFullPath(String fullPath, String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return fullPath.equals(getRootPath(TaskType.Dumper, -1));
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

    public static List<File> listLocalBinlogFiles(String binlogFullPath, String groupName, String streamName) {
        List<File> result = new ArrayList<>();
        String prefix = getBinlogFilePrefix(groupName, streamName);
        File[] localFiles = new File(binlogFullPath).listFiles(((dir, name) -> isBinlogFile(name, prefix)));
        if (localFiles != null) {
            result.addAll(Arrays.asList(localFiles));
        }
        return result;
    }

    public static void deleteBinlogFiles(String binlogFullPath) throws IOException {
        log.warn("try to clean local binlog dir!");
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
        if (CommonUtils.isGlobalBinlogSlave()) {
            return String.format("polardbx_cdc/%s/slave/%s/%s", instId,
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID),
                partName);
        } else {
            return String.format("polardbx_cdc/%s/%s", instId, partName);
        }
    }

}
