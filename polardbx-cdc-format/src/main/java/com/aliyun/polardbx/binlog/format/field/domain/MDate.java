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

package com.aliyun.polardbx.binlog.format.field.domain;

public class MDate {

    private boolean neg;
    private int year;
    private int month;
    private int day;
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
        year = Integer.valueOf(splt[0]);
        month = Integer.valueOf(splt[1]);
        day = Integer.valueOf(splt[2]);
    }

    private void parseHHMMSS(String value) {
        String[] split1 = value.split("\\.");
        String sec = split1[0];
        String[] tsplit = sec.split(":");
        hours = Integer.valueOf(tsplit[0]);
        minutes = Integer.valueOf(tsplit[1]);
        seconds = Integer.valueOf(tsplit[2]);
        if (split1.length == 2) {
            millsecond = Integer.valueOf(split1[1]);
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
}
