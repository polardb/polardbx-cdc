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
package com.aliyun.polardbx.binlog.monitor;

import com.aliyun.polardbx.binlog.AlarmEvent;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.AlarmEventReporter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_FATAL_THRESHOLD_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_LATEST_CONSUME_TIME_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_REPORT_ALARM_EVENT_ENABLED;

/**
 * Created by ziyang.lb
 **/
public class MonitorManager {
    private static final Logger logger = LoggerFactory.getLogger(MonitorManager.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static final int MONITOR_CHECK_INTERVAL = 5;//单位：S，后序进行参数化
    private static final int MONITOR_EXPIRE_TIME = 600;//单位：S，后序进行参数化
    private static final String MONITOR_RANGE_TIME = "00:00-23:59";//后序进行参数化
    private static final String PHONE_SILENCE_TIME = "00:00-06:00";

    private final LoadingCache<MonitorKey, MonitorContent> expirableMonitorCache;
    private final LoadingCache<MonitorKey, MonitorContent> monitorCache;
    private final Map<MonitorType, Long> intervalTimeMap;
    private final ScheduledExecutorService executor;

    private MonitorManager() {
        expirableMonitorCache = CacheBuilder.newBuilder()
            .expireAfterWrite(MONITOR_EXPIRE_TIME, TimeUnit.SECONDS)
            .removalListener((RemovalListener<MonitorKey, MonitorContent>) notification -> {
            }).build(new CacheLoader<MonitorKey, MonitorContent>() {
                @Override
                public MonitorContent load(MonitorKey key) {
                    return new MonitorContent(key.errorMessage);
                }
            });
        monitorCache = CacheBuilder.newBuilder().build(new CacheLoader<MonitorKey, MonitorContent>() {
            @Override
            public MonitorContent load(MonitorKey key) {
                return new MonitorContent(key.errorMessage);
            }
        });
        intervalTimeMap = new ConcurrentHashMap<>();
        executor = Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("monitor-manager-%d").build());
    }

    public static MonitorManager getInstance() {
        return MonitorManagerHolder.instance;
    }

    // 直接同步报警一般不会是阈值触发，所以content为null
    public void triggerAlarmSync(MonitorType monitorType, Object... args) {
        logger.info("receive an alarm trigger sync, monitorType is {}, args is \r\n {}", monitorType, args);
        triggerAlarm(monitorType, null, args);
        check();
    }

    public void triggerAlarm(MonitorType monitorType, Object... args) {
        triggerAlarm(monitorType, null, args);
    }

    public void triggerAlarm(MonitorType monitorType, MonitorValue monitorValue, Object... args) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("receive an alarm trigger, monitorType is {}, args is \r\n {}", monitorType, args);
            }

            String message = MonitorMsgBuilder.buildMessage(monitorType, args);
            if (monitorType.isExpirable()) {
                MonitorContent content = expirableMonitorCache.getUnchecked(new MonitorKey(monitorType, message));
                content.errorCount.incrementAndGet();
                content.errorMessage = message;
                content.monitorValue = monitorValue;
            } else {
                MonitorContent content = monitorCache.getUnchecked(new MonitorKey(monitorType, message));
                content.errorCount.incrementAndGet();
                content.errorMessage = message;
                content.monitorValue = monitorValue;

            }
        } catch (Throwable t) {
            logger.error("something goes wrong when trigger alarm.", t);
        }
    }

    public void startup() {
        this.executor.scheduleAtFixedRate(this::check, 10, MONITOR_CHECK_INTERVAL, TimeUnit.SECONDS);
        logger.info("Monitor Server is started.");
    }

    public void shutdown() {
        this.executor.shutdownNow();
        logger.info("Monitor Server is shutdown.");
    }

    private void check() {
        try {
            Map<MonitorKey, MonitorContent> map1 = expirableMonitorCache.asMap();
            map1.forEach((key, value) -> sendAlarm(key.monitorType, value));

            Map<MonitorKey, MonitorContent> map2 = monitorCache.asMap();
            map2.forEach((key, value) -> sendAlarm(key.monitorType, value));
        } catch (Throwable t) {
            logger.error("Something goes wrong when do monitor.", t);
        }
    }

    private Boolean isAlarm(MonitorType monitorType, MonitorContent monitorValue) {
        try {
            return isValidTime(MONITOR_RANGE_TIME) && monitorValue.errorCount.get() >= monitorType.getAlarmThreshold()
                && isArriveInterval(monitorType);
        } catch (Throwable t) {
            logger.error("something goes wrong when check if do alarm for monitor type {}.", monitorType);
            return false;
        }
    }

    // 如果是在晚上，电话报警不要太敏感，进行多重验证
    private Boolean isAlarmPhone(MonitorType monitorType, MonitorContent monitorValue) {
        try {
            if (isValidTime(PHONE_SILENCE_TIME)) {
                return monitorValue.errorCount.get() >= 10;
            } else {
                return true;
            }
        } catch (Throwable t) {
            logger.error("something goes wrong when check if do alarm for monitor type {}.", monitorType);
            return false;
        }
    }

    private Boolean isArriveInterval(MonitorType monitorType) {
        Long time = intervalTimeMap.get(monitorType);
        if (time == null) {
            intervalTimeMap.put(monitorType, System.currentTimeMillis());
            return true;
        } else {
            Long currentTime = System.currentTimeMillis();
            return currentTime - time > (monitorType.getAlarmInterval() * 60 * 1000);
        }
    }

    private boolean isValidTime(String rangeTime) throws ParseException {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);
        Date date = now.getTime();
        //rangeTime : 00:00-02:30
        String[] range = rangeTime.split("-");
        try {
            Date start = sdf.parse(year + "-" + month + "-" + day + " " + range[0] + ":00");
            Date end = sdf.parse(year + "-" + month + "-" + day + " " + range[1] + ":00");
            return date.after(start) && date.before(end);
        } catch (ParseException e) {
            throw e;
        }
    }

    private void sendAlarm(MonitorType monitorType, MonitorContent monitorContent) {
        try {
            if (monitorContent.errorCount.get() >= monitorType.getAlarmThreshold() &&
                DynamicApplicationConfig.getBoolean(ALARM_REPORT_ALARM_EVENT_ENABLED)) {
                MonitorValue monitorValue = monitorContent.monitorValue;
                AlarmEvent alarmEvent = new AlarmEvent();
                alarmEvent.setEventKey(monitorType.getDesc());
                alarmEvent.setEventValue(monitorContent.errorMessage);
                alarmEvent.setTriggerValue(monitorValue != null ? monitorValue.triggerValue : null);
                AlarmEventReporter.report(alarmEvent);
                if (monitorType.isFatalAlarmIfExistsConsumer() &&
                    System.currentTimeMillis() - DynamicApplicationConfig.getLong(ALARM_LATEST_CONSUME_TIME_MS) <
                        DynamicApplicationConfig.getLong(ALARM_FATAL_THRESHOLD_MS)) {
                    AlarmEvent fatalAlarmEvent = new AlarmEvent();
                    fatalAlarmEvent.setEventKey(MonitorType.BINLOG_FATAL_ERROR.getDesc());
                    fatalAlarmEvent.setEventValue(MonitorMsgBuilder
                        .buildMessage(MonitorType.BINLOG_FATAL_ERROR, monitorContent.errorMessage));
                    fatalAlarmEvent.setTriggerValue(null);
                    AlarmEventReporter.report(fatalAlarmEvent);
                }
            }
        } catch (Throwable t) {
            logger.error("send alarm event failed!", t);
        }
    }

    private static class MonitorManagerHolder {
        static MonitorManager instance = new MonitorManager();
    }

    private static class MonitorKey {
        MonitorType monitorType;
        String errorMessage;

        public MonitorKey(MonitorType monitorType, String errorMessage) {
            this.monitorType = monitorType;
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MonitorKey that = (MonitorKey) o;
            return monitorType == that.monitorType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(monitorType);
        }
    }

    private static class MonitorContent {
        String errorMessage;
        AtomicInteger errorCount;
        MonitorValue monitorValue;

        public MonitorContent(String errorMessage) {
            this.errorMessage = errorMessage;
            this.errorCount = new AtomicInteger(0);
        }
    }
}
