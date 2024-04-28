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
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.util.StorageSequence.getFixedLengthStorageSeq;

/**
 * @author ziyang.lb
 **/
public class CommonUtils {
    public static final String RDS_HIDDEN_PK_NAME = "__#alibaba_rds_row_id#__";
    public static final String PRIVATE_DDL_TSO_PREFIX = "# POLARX_TSO=";
    public static final String PRIVATE_DDL_ENCODE_BASE64 = "# POLARX_ORIGIN_SQL_ENCODE=BASE64";
    public static final String PRIVATE_DDL_DDL_PREFIX = "# POLARX_ORIGIN_SQL=";
    public static final Pattern PRIVATE_DDL_SQL_PATTERN = Pattern.compile(PRIVATE_DDL_DDL_PREFIX + "([\\W\\w]+)");
    public static final String PRIVATE_DDL_ID_PREFIX = "# POLARX_DDL_ID=";
    public static final String PRIVATE_DDL_DDL_ROUTE_PREFIX = "# POLARX_DDL_ROUTE_MODE=";
    private static final Logger logger = LoggerFactory.getLogger(CommonUtils.class);
    private static final long TWEPOCH = 1303895660503L;
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String EMPTY_IP = "0.0.0.0";
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3,}");
    private static final Random random = new Random(System.currentTimeMillis());
    private static final AtomicLong localClock = new AtomicLong();
    private static final ThreadLocal<Long> lastEpochLocal = new ThreadLocal<>();
    private static final ThreadLocal<Long> lastEpochSequenceLocal = new ThreadLocal<>();
    private static final String BINLOG_FILE_PREFIX = "binlog.";

    public static String parsePureTso(String extTso) {
        String[] array = extTso.split("_");
        return array[0];
    }

    public static void sleep(long mills) throws InterruptedException {
        Thread.sleep(mills);
    }

    /**
     * 返回秒级tso中的物理时间戳
     */
    public static Long getTsoPhysicalTime(String tso, TimeUnit timeUnit) {
        Long tsoLong = getTsoTimestamp(tso);
        return tso2physicalTime(tsoLong, timeUnit);
    }

    public static Long tso2physicalTime(Long tsoLong, TimeUnit timeUnit) {
        if (timeUnit == TimeUnit.MILLISECONDS) {
            return (tsoLong >> 22);
        } else if (timeUnit == TimeUnit.SECONDS) {
            return (tsoLong >> 22) / 1000;
        } else {
            throw new UnsupportedOperationException("Unsupported time unit :" + timeUnit);
        }
    }

    /**
     * 返回Tso的高位（即：真实Tso + 事务ID）
     */
    public static String getActualTso(String tso) {
        return tso.substring(0, 38);
    }

    public static Long getTsoTimestamp(String tso) {
        return Long.valueOf(tso.substring(0, 19));
    }

    public static Long getTransactionId(String tso) {
        return Long.valueOf(tso.substring(19, 38));
    }

    public static long randomLong() {
        return random.nextLong();
    }

    public static long randomXid() {
        long epoch = (System.currentTimeMillis() - TWEPOCH) << 22;
        Long seq = lastEpochSequenceLocal.get();
        if (seq == null) {
            seq = 0L;
        }
        if (lastEpochLocal.get() == null || lastEpochLocal.get() != epoch) {
            seq = 0L;
            lastEpochLocal.set(epoch);
            lastEpochSequenceLocal.set(seq);
        } else {
            seq++;
            lastEpochSequenceLocal.set(seq);
        }
        return epoch | seq;
    }

    public static long nextLocalTso() {
        return next(System.currentTimeMillis() << 22);
    }

    /**
     * 新的TSO 生成算法
     * true tso | transactionId | seq | storageInstanceId
     * 19位     |  19           | 10位|   6位
     * 1、真实TSO ，按照TSO 排序。
     * 2、no TSO事务， TSO排序，后再按照transactionId
     * 3、 在以上的基础上 按照物理顺序 seq 排列
     * 4、 接着按照 storageInstanceId 排序
     */
    public static String generateTSO(long currentTso, String transactionId, String storageInstanceId) {
        return StringUtils.leftPad(currentTso + "", 19, "0")
            + transactionId
            + getFixedLengthStorageSeq(storageInstanceId);
    }

    public static String unwrap(String table) {
        table = table.trim();
        int start = 0;
        int end = table.length();
        if (table.startsWith("`")) {
            start++;
        }
        if (table.endsWith("`")) {
            end--;
        }
        return table.substring(start, end).trim();
    }

    private static long next(long threshold) {
        // Make sure localClock is beyond the threshold
        long last = localClock.get();
        while (last < threshold && !localClock.compareAndSet(last, threshold)) {
            last = localClock.get();
        }
        return localClock.incrementAndGet();
    }

    public static String getHostIp() {
        InetAddress address = getHostAddress();
        return address == null ? null : address.getHostAddress();
    }

    public static String getHostName() {
        InetAddress address = getHostAddress();
        return address == null ? null : address.getHostName();
    }

    /**
     * 需要转义，比如表名为test`backtick，在sql语句中应该是如下的形式
     * <p>
     * CREATE TABLE IF NOT EXISTS `test``backtick` (
     * c1 int,
     * _drds_implicit_id_ bigint AUTO_INCREMENT,
     * PRIMARY KEY (_drds_implicit_id_)
     * )
     */
    public static String escape(String str) {
        String regex = "(?<!`)`(?!`)";
        return str.replaceAll(regex, "``");
    }

    public static InetAddress getHostAddress() {
        InetAddress localAddress = null;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isValidHostAddress(localAddress)) {
                return localAddress;
            }
        } catch (Throwable e) {
            logger.warn("Failed to retriving local host ip address, try scan network card ip address. cause: "
                + e.getMessage());
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    try {
                        NetworkInterface network = interfaces.nextElement();
                        Enumeration<InetAddress> addresses = network.getInetAddresses();
                        if (addresses != null) {
                            while (addresses.hasMoreElements()) {
                                try {
                                    InetAddress address = addresses.nextElement();
                                    if (isValidHostAddress(address)) {
                                        return address;
                                    }
                                } catch (Throwable e) {
                                    logger.warn("Failed to retriving network card ip address. cause:" + e.getMessage());
                                }
                            }
                        }
                    } catch (Throwable e) {
                        logger.warn("Failed to retriving network card ip address. cause:" + e.getMessage());
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("Failed to retriving network card ip address. cause:" + e.getMessage());
        }
        logger.error("Could not get local host ip address, will use 127.0.0.1 instead.");
        return localAddress;
    }

    private static boolean isValidHostAddress(InetAddress address) {
        if (address == null || address.isLoopbackAddress()) {
            return false;
        }
        String name = address.getHostAddress();
        return (name != null && !EMPTY_IP.equals(name) && !LOCALHOST_IP.equals(name) && IP_PATTERN.matcher(name)
            .matches());
    }

    public static MarkInfo getCommand(String rowLogsQuery) {
        return new MarkInfo(rowLogsQuery);
    }

    public static Date parse(String date, String pattern) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.parse(date);
    }

    /**
     * 根据指定概率随机true值
     * rate取值为 0~100
     *
     * @param rate 0~100
     */
    public static boolean randomBoolean(int rate) {
        return random.nextInt(100) < rate;
    }

    /**
     * 根据group name和stream name判断当前流是否是全局binlog
     */
    public static boolean isGlobalBinlog(String group, String stream) {
        return group.equals(GROUP_NAME_GLOBAL) && stream.equals(STREAM_NAME_GLOBAL);
    }

    public static boolean isCdcStartCommandIdMatch(String commandId) {
        String clusterType = DynamicApplicationConfig.getClusterType();
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String[] params = commandId.split(":");
        // 如果提供了clusterId就匹配一下
        String commandClusterId = null;
        if (params.length > 1) {
            // 提供了clusterId
            commandClusterId = params[0];
            commandId = params[1];
        }
        if (StringUtils.isNotBlank(commandClusterId) && !StringUtils
            .equalsIgnoreCase(clusterId, commandClusterId)) {
            return false;
        }
        if (Objects.equals(clusterType, ClusterType.BINLOG_X.name())) {
            return Objects.equals(commandId, buildStartCmd());
        } else {
            // 单流需要兼容一下cn老版本的情况，老版本commandId会固定写入0
            return Objects.equals(commandId, buildStartCmd()) || Objects.equals(commandId, "0");
        }
    }

    public static String buildStartCmd() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        String content = "";
        if (Objects.equals(clusterType, ClusterType.BINLOG_X.name())) {
            content = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        }
        return clusterType + "_" + content + "_0";
    }

    public static String min(String tso1, String tso2) {
        if (StringUtils.isBlank(tso1)) {
            return tso2;
        }
        if (StringUtils.isBlank(tso2)) {
            return tso1;
        }
        return tso1.compareTo(tso2) > 0 ? tso2 : tso1;
    }

    public static int parseStreamSeq(String streamName) {
        return Integer.parseInt(StringUtils.substringAfterLast(streamName, "_"));
    }

    public static boolean isTsoPolicyTrans(String tso) {
        String seq = StringUtils.substring(tso, 38, 48);
        return "0000000000".equals(seq);
    }

    public static String extractPrivateTSOFromDDL(String privateDDLQuery) {
        Scanner scan = new Scanner(privateDDLQuery);
        while (scan.hasNextLine()) {
            String line = scan.nextLine();
            final String prefix = CommonUtils.PRIVATE_DDL_TSO_PREFIX;
            if (StringUtils.startsWith(line, prefix)) {
                return line.substring(prefix.length());
            }
        }
        return null;
    }

    public static boolean isGlobalBinlogSlave() {
        String clusterRole = DynamicApplicationConfig.getClusterRole();
        return clusterRole.equals(ClusterRole.slave.name());
    }

    public static boolean isLabEnv() {
        return Boolean.parseBoolean(ConfigPropMap.getPropertyValue(ConfigKeys.IS_LAB_ENV));
    }

    public static String getGroupName() {
        String result;
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (StringUtils.equals(clusterType, ClusterType.BINLOG.name())) {
            result = CommonConstants.GROUP_NAME_GLOBAL;
        } else {
            result = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        }
        return result;
    }

    public static String getCurrentStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder buf = new StringBuilder();
        for (StackTraceElement item : stackTrace) {
            buf.append(item.toString());
            buf.append("\n");
        }
        return buf.toString();
    }

    public static String extractPolarxOriginSql(String sqlInput) {
        return extractPolarxOriginSql(sqlInput, true);
    }

    public static String extractPolarxOriginSql(String sqlInput, boolean decode) {
        Scanner scanner = new Scanner(sqlInput);
        while (scanner.hasNextLine()) {
            String str = scanner.nextLine();
            Matcher matcher = PRIVATE_DDL_SQL_PATTERN.matcher(str);
            if (matcher.find()) {
                String originSql = matcher.group(1);
                if (decode && StringUtils.contains(sqlInput, PRIVATE_DDL_ENCODE_BASE64)) {
                    return new String(Base64.getDecoder().decode(originSql));
                } else {
                    return originSql;
                }
            }
        }

        return "";
    }

    public static String tableName(String schemaName, String tableName) {
        return String.format("`%s`.`%s`", escape(schemaName), escape(tableName));
    }
}
