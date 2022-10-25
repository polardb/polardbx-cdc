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

/**
 * @author chengjin.lyf on 2018/4/19 下午6:16
 * @since 3.2.6
 */
public class RdsBackupPolicy {

    /**
     * 数据备份保留天数（7到730天）。
     */
    private String BackupRetentionPeriod;
    /**
     * 数据备份时间，格式：HH:mmZ- HH:mm Z。
     */
    private String PreferredBackupTime;
    /**
     * 数据备份周期。Monday：周一；Tuesday：周二；Wednesday：周三；Thursday：周四；Friday：周五；Saturday：周六；Sunday：周日。
     */
    private String PreferredBackupPeriod;
    /**
     * 日志备份状态。Enable：开启；Disabled：关闭。
     */
    private boolean BackupLog;
    /**
     * 日志备份保留天数（7到730天）。
     */
    private int LogBackupRetentionPeriod;

    public String getBackupRetentionPeriod() {
        return BackupRetentionPeriod;
    }

    public void setBackupRetentionPeriod(String backupRetentionPeriod) {
        BackupRetentionPeriod = backupRetentionPeriod;
    }

    public String getPreferredBackupTime() {
        return PreferredBackupTime;
    }

    public void setPreferredBackupTime(String preferredBackupTime) {
        PreferredBackupTime = preferredBackupTime;
    }

    public String getPreferredBackupPeriod() {
        return PreferredBackupPeriod;
    }

    public void setPreferredBackupPeriod(String preferredBackupPeriod) {
        PreferredBackupPeriod = preferredBackupPeriod;
    }

    public boolean isBackupLog() {
        return BackupLog;
    }

    public void setBackupLog(boolean backupLog) {
        BackupLog = backupLog;
    }

    public int getLogBackupRetentionPeriod() {
        return LogBackupRetentionPeriod;
    }

    public void setLogBackupRetentionPeriod(int logBackupRetentionPeriod) {
        LogBackupRetentionPeriod = logBackupRetentionPeriod;
    }
}
