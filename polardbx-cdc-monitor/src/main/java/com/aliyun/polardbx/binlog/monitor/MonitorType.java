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

package com.aliyun.polardbx.binlog.monitor;

/**
 * Created by ziyang.lb
 */
public enum MonitorType {
    MERGER_STAGE_LOOP_ERROR(
        "polarx_cdc_merger_error",
        true,
        "Cdc Merger Thread出现严重异常，已退出工作，请尽快解决，异常信息：%s",
        1,
        5,
        false),

    MERGER_STAGE_EMPTY_LOOP_EXCEED_THRESHOLD(
        "polarx_cdc_merger_empty_loop_exceed_threshold",
        true,
        "Cdc Merger Thread已连续%s秒未收到数据，请尽快排查Extractor是否发生了Block.",
        1,
        5,
        true),

    DUMPER_STAGE_LEADER_NODATA_ERROR(
        "polarx_cdc_dumper_leader_nodata_error",
        true,
        "Cdc Dumper Leader已连续%s秒未收到数据，请尽快排查同步链路是否发生了Block",
        2,
        5,
        false),

    DUMPER_STAGE_FOLLOWER_NODATA_ERROR(
        "polarx_cdc_dumper_follower_nodata_error",
        true,
        "Cdc Dumper Follower已连续%s秒未收到数据，请尽快排查同步链路是否发生了Block",
        2,
        5,
        false),

    DUMPER_STAGE_LEADER_DELAY(
        "polarx_cdc_dumper_leader_delayed",
        true,
        "Cdc Dumper Leader出现延迟，延迟时间为%sms，请尽快排查Task到Dumper的同步链路",
        2,
        10,
        true),

    DUMPER_STAGE_FOLLOWER_DELAY(
        "polarx_cdc_dumper_follower_delayed",
        true,
        "Cdc Dumper Follower出现延迟，延迟时间为%sms，请尽快排查Task到Dumper，以及Dumper之间的同步链路.",
        2,
        10,
        true),

    EXTRACTOR_NOT_FOUND_POSITION_ERROR(
        "polarx_cdc_extractor_position_expired",
        true,
        "Cdc Extractor start位点过期，已退出工作，请尽快解决，异常信息：%s",
        1,
        10,
        true),

    EXTRACTOR_START_ERROR(
        "polarx_cdc_extractor_start_error",
        true,
        "Cdc Extractor start异常，错误信息：%s",
        1,
        10,
        true),

    EXTRACTOR_CONSUME_ERROR(
        "polarx_cdc_extractor_consume_error",
        true,
        "Cdc Extractor 消费异常，错误信息：%s",
        1,
        10,
        true),

    DUMPER_STAGE_LEADER_FILE_GENERATE_ERROR(
        "polarx_cdc_dumper_leader_file_generate_error",
        true,
        "Cdc Dumper Leader生产逻辑Binlog的主线程出现异常，请尽快排查，异常信息：%s",
        10,
        10,
        true),

    DUMPER_STAGE_FOLLOWER_FILE_SYNC_ERROR(
        "polarx_cdc_dumper_follower_file_sync_error",
        true,
        "Cdc Dumper Follower同步逻辑Binlog的主线程出现异常，请尽快排查，异常信息：%s",
        10,
        10,
        true),

    BINLOG_NUM_LARGE_THEN_WARRNING(
        "polarx_cdc_disk_usage_warning",
        true,
        "Cdc 本地binlog磁盘使用量超过%d,限制容量为%d,请及时关注",
        1,
        10,
        true),

    DAEMON_TASK_ALIVE_WATCHER_ERROR(
        "polarx_cdc_daemon_task_alive_watcher_error",
        true,
        "Daemon进程TaskAliveWatcher定时任务出现异常，请尽快排查，异常信息：%s",
        5,
        10,
        true),

    DAEMON_TOPOLOGY_WATCHER_ERROR(
        "polarx_cdc_daemon_topology_watcher_error",
        true,
        "Daemon进程TopologyWatcher定时任务出现异常，请尽快排查，异常信息：%s",
        3,
        10,
        true),

    DAEMON_POLARX_HEARTBEAT_ERROR(
        "polarx_cdc_daemon_polarx_heartbeat_error",
        true,
        "Daemon进程向PolarX执行Heartbeat的定时任务出现异常，请尽快排查，异常信息：%s",
        20,
        10,
        true
    ),

    DAEMON_PROCESS_DEAD_ERROR(
        "polarx_cdc_daemon_process_dead_error",
        true,
        "Daemon进程探活异常，进程可能已退出，请尽快排查，所属容器ID：%s",
        5,
        10,
        true
    ),

    REPOSITORY_SWITCH_TO_DISK_WARNING(
        "polarx_cdc_repository_switch_to_disk_warning",
        true,
        "Task进程触发了数据落盘，请密切关注，触发落盘原因: %s",
        1,
        60,
        true
    ),

    BINLOG_BACKUP_UPLOAD_ERROR(
        "polarx_cdc_binlog_backup_upload_error",
        true,
        "Binlog文件备份上传出现异常，请尽快排查原因，文件名称： %s",
        5,
        10,
        true
    ),

    PROCESS_HEARTBEAT_TIMEOUT_WARNING(
        "polarx_cdc_process_heartbeat_timeout_warning",
        true,
        "进程%s出现了心跳超时，触发了自动重启，请及时关注.",
        5,
        10,
        true
    ),
    META_DATA_INCONSISTENT_WARNNIN(
        "polarx_cdc_meta_data_inconsistent_warnning",
        true,
        "当前已经没有执行中的DDL任务，CDC元数据仍然存在不一致，请及时关注，DeltaChangeData: %s",
        1,
        10,
        true
    );

    private String desc;
    private boolean expirable;
    private String msgTemplate;
    private int alarmThreshold;
    private int alarmInterval;
    private boolean justDingding;

    MonitorType(String desc, boolean expirable, String msgTemplate, int alarmThreshold, int alarmInterval,
                boolean justDingding) {
        this.desc = desc;
        this.expirable = expirable;
        this.msgTemplate = msgTemplate;
        this.alarmThreshold = alarmThreshold;
        this.alarmInterval = alarmInterval;
        this.justDingding = justDingding;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public boolean isExpirable() {
        return expirable;
    }

    public void setExpirable(boolean expirable) {
        this.expirable = expirable;
    }

    public String getMsgTemplate() {
        return msgTemplate;
    }

    public void setMsgTemplate(String msgTemplate) {
        this.msgTemplate = msgTemplate;
    }

    public int getAlarmThreshold() {
        return alarmThreshold;
    }

    public void setAlarmThreshold(int alarmThreshold) {
        this.alarmThreshold = alarmThreshold;
    }

    public int getAlarmInterval() {
        return alarmInterval;
    }

    public void setAlarmInterval(int alarmInterval) {
        this.alarmInterval = alarmInterval;
    }

    public boolean isJustDingding() {
        return justDingding;
    }

    public void setJustDingding(boolean justDingding) {
        this.justDingding = justDingding;
    }
}
