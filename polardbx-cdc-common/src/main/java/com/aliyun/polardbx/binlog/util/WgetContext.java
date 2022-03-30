package com.aliyun.polardbx.binlog.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class WgetContext {

    private static final Logger logger = LoggerFactory.getLogger(WgetContext.class);
    private static final int SIZE_IDX = 0;
    private static final int SPEED_IDX = 7;
    private static final int PROGRESS_IDX = 6;
    private static LoadingCache<Long, AvgSpeed> speedPerSecMap =
        CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build(
            new CacheLoader<Long, AvgSpeed>() {
                @Override
                public AvgSpeed load(Long s) throws Exception {
                    return new AvgSpeed();
                }
            });

    public static void main(String[] args) {
//        WgetContext.add("196700K .......... .......... .......... .......... .......... 38% 6.50M 48s");
        WgetContext.add("9000K .......... .......... .......... .......... ..........  1%  234K 27m52s");
        WgetContext.add("0K .......... .......... .......... .......... ..........  0%  1  20d6h");
        WgetContext.add("0K .......... .......... .......... .......... ..........  0%  1M  20d6h");
    }

    /**
     * 196700K .......... .......... .......... .......... .......... 38% 6.50M 48s
     */
    public static void add(String log) {
        try {
            String args[] = log.trim().split(" ");
            int idx = 0;
            if (args.length <= SPEED_IDX) {
                logger.info(log);
                return;
            }
            String speedReg = args[SPEED_IDX];
            for (int i = 0; i < args.length; i++) {
                if (StringUtils.isBlank(args[i])) {
                    continue;
                }
                if (idx == SPEED_IDX) {
                    speedReg = args[i];
                    break;
                }
                idx++;
            }
            if (StringUtils.isBlank(speedReg)) {
                return;
            }
            speedReg = speedReg.trim();
            if (speedReg.length() > 1) {
                if (!NumberUtils.isCreatable(speedReg.substring(0, speedReg.length() - 1))) {
                    return;
                }
            } else if (!NumberUtils.isCreatable(speedReg)) {
                return;
            }
            Long speed = convert(speedReg);
            speedPerSecMap.get(Thread.currentThread().getId()).addSpeed(speed);
        } catch (Exception e) {
            logger.error("parse wget log error", e);
        }

    }

    public static void finish() {
        speedPerSecMap.invalidate(Thread.currentThread().getId());
    }

    private static Long convert(String reg) {
        char unit = reg.charAt(reg.length() - 1);
        Long v = 0L;
        switch (unit) {
        case 'M':
            v = (long) (extract(reg) * 1024 * 1024);
            break;
        case 'K':
            v = (long) (extract(reg) * 1024);
            break;
        default:
            // byte
            v = Long.valueOf(reg);
        }
        return v;
    }

    private static double extract(String reg) {
        return Double.parseDouble(reg.substring(0, reg.length() - 1));
    }

    public static long size() {
        return speedPerSecMap.size();
    }

    public static long totalSpeed() {
        long total = 0;
        for (AvgSpeed v : speedPerSecMap.asMap().values()) {
            total += v.eval();
        }
        return total;
    }

    public static class AvgSpeed {
        private long totalSpeed = 0;
        private int count = 0;

        public void addSpeed(long speed) {
            synchronized (this) {
                this.totalSpeed += speed;
                this.count++;
            }
        }

        public long eval() {
            synchronized (this) {
                return totalSpeed / count;
            }
        }
    }
}
