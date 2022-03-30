/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.MarkInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Created by ziyang.lb
 **/
public class CommonUtils {
    private static final Logger logger = LoggerFactory.getLogger(CommonUtils.class);

    private static final long TWEPOCH = 1303895660503L;
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String EMPTY_IP = "0.0.0.0";
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3,}");

    private static final Random random = new Random(System.currentTimeMillis());
    private static final AtomicLong localClock = new AtomicLong();
    private static final ThreadLocal<Long> lastEpochLocal = new ThreadLocal<>();
    private static final ThreadLocal<Long> lastEpochSequenceLocal = new ThreadLocal<>();

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
     * 返回Tso的高位（即：真实Tso）
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

        long storageHashValue = 0;
        // 有些场景下需要获取 RDS 无关的虚拟TSO
        if (StringUtils.isNotBlank(storageInstanceId)) {
            storageHashValue = Math.abs(Objects.hash(storageInstanceId));
        }
        String storageHash = String.valueOf(storageHashValue);
        if (storageHash.length() > 6) {
            storageHash = storageHash.substring(0, 6);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.leftPad(currentTso + "", 19, "0")).
            append(transactionId).
            append(StringUtils.leftPad(storageHash + "", 6, "0"));

        return builder.toString();
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
}
