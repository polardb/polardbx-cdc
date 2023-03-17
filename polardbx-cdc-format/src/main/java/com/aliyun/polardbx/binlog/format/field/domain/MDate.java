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
package com.aliyun.polardbx.binlog.format.field.domain;

import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class MDate {

    /**
     * Usefull constants
     */
    private final long SECONDS_IN_24H = 86400L;
    /**
     * Limits for the TIME data type
     */
    private final int TIME_MAX_HOUR = 838;
    private final int TIME_MAX_MINUTE = 59;
    private final int TIME_MAX_SECOND = 59;
    private MySQLType mySQLType;
    private boolean neg;
    private int year = 1999;
    private int month = 7;
    private int day = 31;
    private int hours;
    private int minutes;
    private int seconds;
    /**
     * unsigned long second_part;  microseconds
     **/
    private int millsecond;

    private long my_packed_time_make(long i, long f) {
        assert (Math.abs(f) <= 0xffffffL);
        return ((i) << 24) + f;
    }

    public void parse(String time) {
        time = StringUtils.trim(time);
        if (time.startsWith("-")) {
            neg = true;
            time = time.substring(1);
        }
        int dot = time.indexOf(".");
        if (dot > 0) {
            int fracLen = time.length() - (dot + 1);
            if (fracLen < 6) {
                time = StringUtils.rightPad(time, time.length() + 6 - fracLen, "0");
            }
        }
        String[] splitTime = time.split(" ");
        if (splitTime.length == 2) {
            parseYYYYMMDD(splitTime[0]);
            parseHHMMSS(splitTime[1]);
        } else if (time.contains("-")) {
            parseYYYYMMDD(time);
        } else if (time.contains(":")) {
            parseHHMMSS(time);
        }
    }

    private void parseYYYYMMDD(String value) {
        String[] splt = value.split("-");
        year = Integer.parseInt(splt[0]);
        month = Integer.parseInt(splt[1]);
        day = Integer.parseInt(splt[2]);
    }

    private void parseHHMMSS(String value) {
        String[] split1 = value.split("\\.");
        String sec = split1[0];
        String[] tsplit = sec.split(":");
        hours = Integer.parseInt(tsplit[0]);
        minutes = Integer.parseInt(tsplit[1]);
        if (tsplit.length == 3) {
            seconds = Integer.parseInt(tsplit[2]);
        }
        if (split1.length == 2) {
            millsecond = Integer.parseInt(split1[1]);
        }
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public boolean isNeg() {
        return neg;
    }

    public void setNeg(boolean neg) {
        this.neg = neg;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getHours() {
        return hours;
    }

    public void setHours(int hours) {
        this.hours = hours;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public int getMillsecond() {
        return millsecond;
    }

    public void setMillsecond(int millsecond) {
        this.millsecond = millsecond;
    }

    public long TIME_to_longlong_time_packed() {
        /* If month is 0, we mix day with hours: "1 00:10:10" -> "24:00:10" */
        long hms = (((getMonth() > 0 ? 0 : getDay() * 24) + getHours()) << 12) | (getMinutes() << 6) | getSeconds();
        long tmp = my_packed_time_make(hms, getMillsecond());
        return isNeg() ? -tmp : tmp;
    }

    public long TIME_to_ulonglong_datetime() {
        return ((year * 10000L + month * 100L + day) * 1000000L + (hours * 10000L + minutes * 100L + seconds));
    }

    public long TIME_to_timestamp() {
        return ((year * 10000L + month * 100L + day) * 1000000L + (hours * 10000L + minutes * 100L + seconds));
    }

    public long TIME_to_longlong_datetime_packed() {
        long ymd = ((year * 13 + month) << 5) | day;
        long hms = (hours << 12) | (minutes << 6) | seconds;
        long tmp = my_packed_time_make(((ymd << 17) | hms), millsecond);
        assert (!check_datetime_range()); /* Make sure no overflow */
        return neg ? -tmp : tmp;
    }

    boolean check_datetime_range() {
  /*
    In case of MYSQL_TIMESTAMP_TIME hour value can be up to TIME_MAX_HOUR.
    In case of MYSQL_TIMESTAMP_DATETIME it cannot be bigger than 23.
  */
        return year > 9999 || month > 12 || day > 31 ||
            minutes > 59 || seconds > 59 ||
            millsecond > 999999 ||
            (hours >
                (mySQLType == MySQLType.MYSQL_TYPE_TIMESTAMP ? TIME_MAX_HOUR : 23));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MDate mDate = (MDate) o;
        return neg == mDate.neg &&
            year == mDate.year &&
            month == mDate.month &&
            day == mDate.day &&
            hours == mDate.hours &&
            minutes == mDate.minutes &&
            seconds == mDate.seconds &&
            millsecond == mDate.millsecond;
    }

    @Override
    public int hashCode() {
        return Objects.hash(neg, year, month, day, hours, minutes, seconds, millsecond);
    }
}
