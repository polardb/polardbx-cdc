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

package com.aliyun.polardbx.rpl.common;

import java.util.Date;

/**
 * MySQL针对时间解析的java版实现
 *
 * @author agapple 2017年4月19日 上午11:12:20
 * @since 3.2.3
 */
public class DateParseUtil {

    public static final int TIME_DATETIME_ONLY = 0;
    public static int internal_format_positions[] = {0, 1, 2, 3, 4, 5, 6, 255};
    public static final int MAX_DATE_PARTS = 8;
    // long log_10_int[] = new long[] { 1, 10, 100, 1000, 10000L, 100000L,
    // 1000000L, 10000000L, 100000000L };
    public static final int YY_PART_YEAR = 70;

    @SuppressWarnings({"deprecation", "unused"})
    public static Date str_to_time(String str) {
        int length = str.length();
        int was_cut;
        Date time = new Date(0);
        int field_length;
        int digits;
        int year_length = 0;
        int add_hours = 0, start_loop;
        long not_zero_date, allow_space;
        boolean is_internal_format;
        int pos;
        int last_field_pos = 0;
        int cur_pos = 0;
        int i;
        int date[] = new int[MAX_DATE_PARTS];
        int date_len[] = new int[MAX_DATE_PARTS];
        int number_of_fields;
        boolean found_delimitier = false, found_space = false;
        int frac_pos;
        int frac_len;
        // DBUG_ENTER("str_to_datetime");
        // DBUG_PRINT("ENTER",("str: %.*s",length,str));
        //
        // LINT_INIT(field_length);

        was_cut = 0;

        /* Skip space at start */
        str = str.trim();
        // for (; str != end && my_isspace(&my_charset_latin1, *str) ; str++)
        // ;
        int end = str.length();
        if (str.isEmpty() || !isdigit(str.charAt(0))) {
            was_cut = 1;
            return null;
        }

        is_internal_format = false;
        int format_position[] = internal_format_positions;

        for (pos = 0; pos < str.length() && (isdigit(str.charAt(pos)) || str.charAt(pos) == 'T'); pos++) {
        }
        ;
        digits = pos;
        // digits= (uint) (pos-str);
        start_loop = 0; /* Start of scan loop */
        date_len[format_position[0]] = 0; /* Length of year field */

        if (pos == str.length() || str.charAt(pos) == '.') {
            /* Found date in internal format (only numbers like YYYYMMDD) */
            year_length = (digits == 4 || digits == 8 || digits >= 14) ? 4 : 2;
            field_length = year_length;
            is_internal_format = true;
            format_position = internal_format_positions;
        } else {
            if (format_position[0] >= 3) /* If year is after HHMMDD */ {

                while (pos < end && !my_isspace(str.charAt(pos))) {
                    pos++;
                }
                while (pos < end && !isdigit(str.charAt(pos))) {
                    pos++;
                }
                if (pos == end) {
                    /* Date field. Set hour, minutes and seconds to 0 */
                    date[0] = date[1] = date[2] = date[3] = date[4] = 0;
                    start_loop = 5; /* Start with first date part */
                }
            }

            field_length = format_position[0] == 0 ? 4 : 2;
        }

        /*
         * Only allow space in the first "part" of the datetime field and: - after days, part seconds - before and after
         * AM/PM (handled by code later) 2003-03-03 20:00:20 AM 20:00:20.000000 AM 03-03-2000
         */
        i = max(format_position[0], format_position[1]);

        if (i < format_position[2]) {
            i = format_position[2];
        }
        // set_if_bigger(i,format_position[2]);
        allow_space = ((1 << i) | (1 << format_position[6]));
        allow_space &= (1 | 2 | 4 | 8);

        not_zero_date = 0;
        for (i = start_loop; i < MAX_DATE_PARTS - 1 && cur_pos != end && isdigit(str.charAt(cur_pos)); i++) {
            int start = cur_pos;
            int tmp_value = (str.charAt(cur_pos++) - '0');

            boolean scan_until_delim = !is_internal_format && ((i != format_position[6]));

            while (cur_pos != end && isdigit(str.charAt(cur_pos)) && (scan_until_delim || (--field_length != 0))) {
                tmp_value = tmp_value * 10 + (str.charAt(cur_pos) - '0');
                cur_pos++;
            }
            date_len[i] = (cur_pos - start);
            if (tmp_value > 999999) /* Impossible date part */ {
                was_cut = 1;
                return null;
            }
            date[i] = tmp_value;
            not_zero_date |= tmp_value;

            /* Length of next field */
            field_length = format_position[i + 1] == 0 ? 4 : 2;

            if ((last_field_pos = cur_pos) == end) {
                i++; /* Register last found part */
                break;
            }
            if (i == format_position[2] && str.charAt(cur_pos) == 'T') {
                cur_pos++; /* ISO8601: CCYYMMDDThhmmss */
                continue;
            }
            if (i == format_position[5]) /* Seconds */ {
                if (str.charAt(cur_pos) == '.') /* Followed by part seconds */ {
                    cur_pos++;
                    field_length = 6; /* 6 digits */
                }
                continue;
            }
            while (cur_pos != end && (my_ispunct(str.charAt(cur_pos)) || my_isspace(str.charAt(cur_pos)))) {
                if (my_isspace(str.charAt(cur_pos))) {
                    if (!((allow_space & (1 << i)) != 0)) {
                        was_cut = 1;
                        return null;
                    }
                    found_space = true;
                }
                cur_pos++;
                found_delimitier = true; /* Should be a 'normal' date */
            }
            /* Check if next position is AM/PM */
            if (i == format_position[6]) /* Seconds, time for AM/PM */ {
                i++; /* Skip AM/PM part */
                if (format_position[7] != 255) /* If using AM/PM */ {
                    if (cur_pos + 2 <= end && (str.charAt(cur_pos + 1) == 'M' || str.charAt(cur_pos) + 1 == 'm')) {
                        if (str.charAt(cur_pos) == 'p' || str.charAt(cur_pos) == 'P') {
                            add_hours = 12;
                        } else if (str.charAt(cur_pos) != 'a' || str.charAt(cur_pos) != 'A') {
                            continue; /*
                             * Not AM / PM
                             */
                        }
                        str += 2; /* Skip AM/PM */
                        /* Skip space after AM/PM */
                        while (cur_pos != end && my_isspace(str.charAt(cur_pos))) {
                            cur_pos++;
                        }
                    }
                }
            }
            last_field_pos = cur_pos;
        }

        cur_pos = last_field_pos;

        number_of_fields = i - start_loop;
        while (i < MAX_DATE_PARTS) {
            date_len[i] = 0;
            date[i++] = 0;
        }

        if (!is_internal_format) {
            year_length = date_len[format_position[0]];
            if (!(year_length != 0)) /* Year must be specified */ {
                was_cut = 1;
                return null;
            }
            time.setYear(date[format_position[0]] - 1900);
            // l_time->year= date[(uint) format_position[0]];
            time.setMonth(date[format_position[1]] - 1);
            // l_time->month= date[(uint) format_position[1]];
            time.setDate(date[format_position[2]]);
            // l_time->day= date[(uint) format_position[2]];
            time.setHours(date[format_position[3]]);
            time.setMinutes(date[format_position[4]]);
            time.setSeconds(date[format_position[5]]);
            // l_time->hour= date[(uint) format_position[3]];
            // l_time->minute= date[(uint) format_position[4]];
            // l_time->second= date[(uint) format_position[5]];

            frac_pos = format_position[6];
            frac_len = date_len[frac_pos];
            // if (frac_len < 6)
            // date[frac_pos]*= log_10_int[6 - frac_len];
            // l_time->second_part= date[frac_pos];

            if (format_position[7] != 255) {
                if (time.getHours() > 12) {
                    was_cut = 1;
                    return null;
                }
                time.setHours(time.getHours() % 12 + add_hours);
            }
        } else {
            time.setYear(date[0] - 1900);
            time.setMonth(date[1] - 1);
            time.setDate(date[2]);
            time.setHours(date[3]);
            time.setMinutes(date[4]);
            time.setSeconds(date[5]);
            // if (date_len[6] < 6)
            // date[6]*= log_10_int[6 - date_len[6]];
            // l_time->second_part=date[6];
        }
        // l_time->neg= 0;

        if (year_length == 2 && (not_zero_date != 0)) {
            time.setYear(time.getYear() + (time.getYear() < YY_PART_YEAR ? 2000 : 1900));
        }
        // l_time->year+= (l_time->year < YY_PART_YEAR ? 2000 : 1900);

        if (number_of_fields < 3 || time.getYear() > 9999 || time.getMonth() > 12 || time.getDate() > 31
            || time.getHours() > 23 || time.getMinutes() > 59 || time.getMinutes() > 59) {
            /* Only give warning for a zero date if there is some garbage after */
            if (!(not_zero_date != 0)) /* If zero date */ {
                for (; cur_pos != end; cur_pos++) {
                    if (!my_isspace(str.charAt(cur_pos))) {
                        not_zero_date = 1; /* Give warning */
                        break;
                    }
                }
            }
            // was_cut= test(not_zero_date);
            return null;
        }

        if (check_date(time, not_zero_date != 0, was_cut)) {
            return null;
        }

        // l_time->time_type= (number_of_fields <= 3 ?
        // MYSQL_TIMESTAMP_DATE : MYSQL_TIMESTAMP_DATETIME);

        for (; cur_pos != end; cur_pos++) {
            if (!my_isspace(str.charAt(cur_pos))) {
                was_cut = 1;
                break;
            }
        }

        return time;
    }

    public static boolean check_date(Date time, boolean not_zero_date, int was_cut) {
        return false;
    }

    private static int uplimits[] = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    /**
     * 检查日期是否合法，大部分从上面函数借鉴过来
     */
    @SuppressWarnings({"deprecation", "unused"})
    public static Boolean check(String str) {
        int length = str.length();
        int was_cut;
        Date time = new Date(0);
        int field_length;
        int digits;
        int year_length = 0;
        int add_hours = 0, start_loop;
        long not_zero_date, allow_space;
        boolean is_internal_format;
        int pos;
        int last_field_pos = 0;
        int cur_pos = 0;
        int i;
        int date[] = new int[MAX_DATE_PARTS];
        int date_len[] = new int[MAX_DATE_PARTS];
        int number_of_fields;
        boolean found_delimitier = false, found_space = false;
        int frac_pos;
        int frac_len;

        was_cut = 0;

        /* Skip space at start */
        str = str.trim();
        int end = str.length();
        if (str.isEmpty() || !isdigit(str.charAt(0))) {
            was_cut = 1;
            return null;
        }

        is_internal_format = false;
        /*
         * This has to be changed if want to activate different timestamp formats
         */
        int format_position[] = internal_format_positions;

        /*
         * Calculate number of digits in first part. If length= 8 or >= 14 then year is of format YYYY. (YYYY-MM-DD,
         * YYYYMMDD, YYYYYMMDDHHMMSS)
         */

        for (pos = 0; pos < str.length() && (isdigit(str.charAt(pos)) || str.charAt(pos) == 'T'); pos++) {
            ;
        }

        digits = pos;
        // digits= (uint) (pos-str);
        start_loop = 0; /* Start of scan loop */
        date_len[format_position[0]] = 0; /* Length of year field */

        if (pos == str.length() || str.charAt(pos) == '.') {
            /* Found date in internal format (only numbers like YYYYMMDD) */
            year_length = (digits == 4 || digits == 8 || digits >= 14) ? 4 : 2;
            field_length = year_length;
            is_internal_format = true;
            format_position = internal_format_positions;
        } else {
            if (format_position[0] >= 3) /* If year is after HHMMDD */ {
                /*
                 * If year is not in first part then we have to determinate if we got a date field or a datetime field.
                 * We do this by checking if there is two numbers separated by space in the input.
                 */
                while (pos < end && !my_isspace(str.charAt(pos))) {
                    pos++;
                }
                while (pos < end && !isdigit(str.charAt(pos))) {
                    pos++;
                }
                if (pos == end) {
                    /* Date field. Set hour, minutes and seconds to 0 */
                    date[0] = date[1] = date[2] = date[3] = date[4] = 0;
                    start_loop = 5; /* Start with first date part */
                }
            }

            field_length = format_position[0] == 0 ? 4 : 2;
        }

        /*
         * Only allow space in the first "part" of the datetime field and: - after days, part seconds - before and after
         * AM/PM (handled by code later) 2003-03-03 20:00:20 AM 20:00:20.000000 AM 03-03-2000
         */
        i = max(format_position[0], format_position[1]);

        if (i < format_position[2]) {
            i = format_position[2];
        }
        // set_if_bigger(i,format_position[2]);
        allow_space = ((1 << i) | (1 << format_position[6]));
        allow_space &= (1 | 2 | 4 | 8);

        not_zero_date = 0;
        for (i = start_loop; i < MAX_DATE_PARTS - 1 && cur_pos != end && isdigit(str.charAt(cur_pos)); i++) {
            int start = cur_pos;
            int tmp_value = (str.charAt(cur_pos++) - '0');

            /*
             * Internal format means no delimiters; every field has a fixed width. Otherwise, we scan until we find a
             * delimiter and discard leading zeroes -- except for the microsecond part, where leading zeroes are
             * significant, and where we never process more than six digits.
             */
            boolean scan_until_delim = !is_internal_format && ((i != format_position[6]));

            while (cur_pos != end && isdigit(str.charAt(cur_pos)) && (scan_until_delim || (--field_length != 0))) {
                tmp_value = tmp_value * 10 + (str.charAt(cur_pos) - '0');
                cur_pos++;
            }
            date_len[i] = (cur_pos - start);
            if (tmp_value > 999999) /* Impossible date part */ {
                was_cut = 1;
                return null;
            }
            date[i] = tmp_value;
            not_zero_date |= tmp_value;

            /* Length of next field */
            field_length = format_position[i + 1] == 0 ? 4 : 2;

            if ((last_field_pos = cur_pos) == end) {
                i++; /* Register last found part */
                break;
            }
            /* Allow a 'T' after day to allow CCYYMMDDT type of fields */
            if (i == format_position[2] && str.charAt(cur_pos) == 'T') {
                cur_pos++; /* ISO8601: CCYYMMDDThhmmss */
                continue;
            }
            if (i == format_position[5]) /* Seconds */ {
                if (str.charAt(cur_pos) == '.') /* Followed by part seconds */ {
                    cur_pos++;
                    field_length = 6; /* 6 digits */
                }
                continue;
            }
            while (cur_pos != end && (my_ispunct(str.charAt(cur_pos)) || my_isspace(str.charAt(cur_pos)))) {
                if (my_isspace(str.charAt(cur_pos))) {
                    if (!((allow_space & (1 << i)) != 0)) {
                        was_cut = 1;
                        return null;
                    }
                    found_space = true;
                }
                cur_pos++;
                found_delimitier = true; /* Should be a 'normal' date */
            }
            /* Check if next position is AM/PM */
            if (i == format_position[6]) /* Seconds, time for AM/PM */ {
                i++; /* Skip AM/PM part */
                if (format_position[7] != 255) /* If using AM/PM */ {
                    if (cur_pos + 2 <= end && (str.charAt(cur_pos + 1) == 'M' || str.charAt(cur_pos) + 1 == 'm')) {
                        if (str.charAt(cur_pos) == 'p' || str.charAt(cur_pos) == 'P') {
                            add_hours = 12;
                        } else if (str.charAt(cur_pos) != 'a' || str.charAt(cur_pos) != 'A') {
                            continue; /*
                             * Not AM / PM
                             */
                        }
                        str += 2; /* Skip AM/PM */
                        /* Skip space after AM/PM */
                        while (cur_pos != end && my_isspace(str.charAt(cur_pos))) {
                            cur_pos++;
                        }
                    }
                }
            }
            last_field_pos = cur_pos;
        }

        cur_pos = last_field_pos;

        number_of_fields = i - start_loop;
        while (i < MAX_DATE_PARTS) {
            date_len[i] = 0;
            date[i++] = 0;
        }

        if (!is_internal_format) {
            year_length = date_len[format_position[0]];
            if (!(year_length != 0)) /* Year must be specified */ {
                was_cut = 1;
                return null;
            }
            int year = date[format_position[0]];
            int month = date[format_position[1]];
            int day = date[format_position[2]];
            int hours = date[format_position[3]];
            int mins = date[format_position[4]];
            int seconds = date[format_position[5]];
            if (!checkDateTime(year_length, not_zero_date, year, month, day, hours, mins, seconds)) {
                return false;
            }
            time.setYear(date[format_position[0]] - 1900);
            // l_time->year= date[(uint) format_position[0]];
            time.setMonth(date[format_position[1]] - 1);
            // l_time->month= date[(uint) format_position[1]];
            time.setDate(date[format_position[2]]);
            // l_time->day= date[(uint) format_position[2]];
            time.setHours(date[format_position[3]]);
            time.setMinutes(date[format_position[4]]);
            time.setSeconds(date[format_position[5]]);
            // l_time->hour= date[(uint) format_position[3]];
            // l_time->minute= date[(uint) format_position[4]];
            // l_time->second= date[(uint) format_position[5]];

            frac_pos = format_position[6];
            frac_len = date_len[frac_pos];
            // if (frac_len < 6)
            // date[frac_pos]*= log_10_int[6 - frac_len];
            // l_time->second_part= date[frac_pos];

            if (format_position[7] != 255) {
                if (time.getHours() > 12) {
                    was_cut = 1;
                    return null;
                }
                time.setHours(time.getHours() % 12 + add_hours);
            }
        } else {

            int year = date[0];
            int month = date[1];
            int day = date[2];
            int hours = date[3];
            int mins = date[4];
            int seconds = date[5];

            if (!checkDateTime(year_length, not_zero_date, year, month, day, hours, mins, seconds)) {
                return false;
            }

            time.setYear(date[0] - 1900);
            time.setMonth(date[1] - 1);
            time.setDate(date[2]);

            time.setHours(date[3]);
            time.setMinutes(date[4]);
            time.setSeconds(date[5]);
        }

        if (number_of_fields < 3 || time.getYear() > 9999 || time.getMonth() > 12 || time.getDate() > 31
            || time.getHours() > 23 || time.getMinutes() > 59 || time.getMinutes() > 59) {
            /* Only give warning for a zero date if there is some garbage after */
            if (!(not_zero_date != 0)) /* If zero date */ {
                for (; cur_pos != end; cur_pos++) {
                    if (!my_isspace(str.charAt(cur_pos))) {
                        not_zero_date = 1; /* Give warning */
                        break;
                    }
                }
            }
            // was_cut= test(not_zero_date);
            return null;
        }

        return true;
    }

    /**
     *
     */
    private static boolean checkDateTime(int year_length, long not_zero_date, int year, int month, int day, int hours,
                                         int mins, int seconds) {

        if (month > 12 || month <= 0) {
            return false;
        }
        if (year_length == 2 && (not_zero_date != 0)) {
            year = year + (year < YY_PART_YEAR ? 2000 : 1900);
        }
        int uplimit = uplimits[month];

        if (day != 2) {
            if (day > uplimit || day <= 0) {
                return false;
            }
        } else {
            if (isLeap(year)) {
                if (day > 29 || day <= 0) {
                    return false;
                }
            } else {
                if (day > 28 || day <= 0) {
                    return false;
                }
            }
        }
        if (hours > 23 || hours < 0) {
            return false;
        }
        if (mins > 59 || mins < 0) {
            return false;
        }
        if (seconds > 59 || seconds < 0) {
            return false;
        }
        return true;
    }

    /**
     *
     */
    private static boolean isLeap(int year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);

    }

    public static boolean my_ispunct(char v) {
        switch (Character.getType(v)) {
        case Character.CONNECTOR_PUNCTUATION:
        case Character.DASH_PUNCTUATION:
        case Character.END_PUNCTUATION:
        case Character.FINAL_QUOTE_PUNCTUATION:
        case Character.INITIAL_QUOTE_PUNCTUATION:
        case Character.START_PUNCTUATION:
        case Character.OTHER_PUNCTUATION:
            return true;
        }

        return false;
    }

    public static int max(int i, int j) {
        return i > j ? i : j;
    }

    public static boolean my_isspace(char v) {
        return Character.isWhitespace(v);
    }

    public static boolean isdigit(char v) {
        return Character.isDigit(v);
    }

}
