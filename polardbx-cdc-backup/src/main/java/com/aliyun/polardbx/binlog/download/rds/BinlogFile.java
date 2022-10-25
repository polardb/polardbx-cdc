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
package com.aliyun.polardbx.binlog.download.rds;

import lombok.Data;
import org.apache.commons.lang3.time.FastTimeZone;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:41
 * @since 3.2.6
 */
@Data
public class BinlogFile implements Comparable<BinlogFile> {
    private Long FileSize;
    private String LogBeginTime;
    private String LogEndTime;
    private String DownloadLink;
    private Long InstanceID;
    private String LinkExpiredTime;
    private String IntranetDownloadLink;
    private String Logname;

    private Long beginTime;
    private Long endTime;

    public static Long format(String utc) throws ParseException {
        if (utc == null) {
            return null;
        }
        Date date = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(FastTimeZone.getGmtTimeZone());
        date = sdf.parse(utc);
        return date.getTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BinlogFile that = (BinlogFile) o;
        return Objects.equals(FileSize, that.FileSize) &&
            Objects.equals(LogBeginTime, that.LogBeginTime) &&
            Objects.equals(LogEndTime, that.LogEndTime) &&
            Objects.equals(DownloadLink, that.DownloadLink) &&
            Objects.equals(InstanceID, that.InstanceID) &&
            Objects.equals(LinkExpiredTime, that.LinkExpiredTime) &&
            Objects.equals(Logname, that.Logname) &&
            Objects.equals(IntranetDownloadLink, that.IntranetDownloadLink);
    }

    public void initRegionTime() throws ParseException {
        this.beginTime = format(LogBeginTime);
        this.endTime = format(LogEndTime);
    }

    public boolean contain(Long time) {
        long lb = beginTime;
        long le = endTime;
        return lb < time && time <= le;
    }

    @Override
    public String toString() {
        return "BinlogFile{" +
            "FileSize=" + FileSize +
            ", LogBeginTime='" + LogBeginTime + '\'' +
            ", LogEndTime='" + LogEndTime + '\'' +
//            ", DownloadLink='" + DownloadLink + '\'' +
            ", InstanceID=" + InstanceID +
            ", LinkExpiredTime='" + LinkExpiredTime + '\'' +
//            ", IntranetDownloadLink='" + IntranetDownloadLink + '\'' +
            ", Logname='" + Logname + '\'' +
            ", beginTime=" + beginTime +
            ", endTime=" + endTime +
            '}';
    }

    @Override
    public int hashCode() {

        return Objects.hash(FileSize, LogBeginTime, LogEndTime, DownloadLink, InstanceID, LinkExpiredTime,
            IntranetDownloadLink);
    }

    @Override
    public int compareTo(BinlogFile o) {
        return beginTime > o.beginTime && endTime > o.endTime ? 1 : -1;
    }
}
