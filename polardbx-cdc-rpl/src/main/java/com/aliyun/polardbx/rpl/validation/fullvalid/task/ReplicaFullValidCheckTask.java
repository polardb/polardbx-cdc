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
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.Constants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.RplFullValidDiffMapper;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidDiff;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidDiffStatus;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidSqlGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.ArrayUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author yudong
 * @since 2023/10/24 14:46
 **/
public class ReplicaFullValidCheckTask extends ReplicaFullValidSubTask {

    private static final Logger log = LoggerFactory.getLogger("fullValidLogger");

    private static final RplFullValidSubTaskMapper subTaskMapper =
        SpringContextHolder.getObject(RplFullValidSubTaskMapper.class);
    private static final RplFullValidDiffMapper diffMapper =
        SpringContextHolder.getObject(RplFullValidDiffMapper.class);
    private final ReplicaFullValidSubTaskContext context;
    private final long taskId;
    private DataSource srcDataSource;
    private DataSource dstDataSource;
    private JdbcTemplate srcJdbcTemplate;
    private JdbcTemplate dstJdbcTemplate;
    private final TaskConfig config;
    private TableInfo srcTableInfo;
    private TableInfo dstTableInfo;
    private TaskSummary summary;

    public ReplicaFullValidCheckTask(ReplicaFullValidSubTaskContext context) {
        this.context = context;
        this.taskId = context.getSubTaskId();
        Optional<RplFullValidSubTask> subTask = subTaskMapper.selectOne(
            s -> s.where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
        if (!subTask.isPresent()) {
            throw new RuntimeException("Failed to find sub task:" + taskId);
        }
        config = JSON.parseObject(subTask.get().getTaskConfig(), TaskConfig.class);
    }

    @Override
    public void run() {
        try {
            MDC.put(Constants.MDC_RPL_FULL_VALID_TASK_ID_KEY, String.valueOf(context.getSubTaskId()));

            // IMPORTANT: 这里需要recheck一下subtask的状态是否还是ready，
            boolean switchSucc =
                ReplicaFullValidTaskManager.switchSubTaskState(context.getSubTaskId(), ReplicaFullValidTaskState.READY,
                    ReplicaFullValidTaskState.RUNNING);
            if (!switchSucc) {
                return;
            }

            summary = new TaskSummary();

            check();

            subTaskMapper.update(
                r -> r.set(RplFullValidSubTaskDynamicSqlSupport.summary).equalTo(JSON.toJSONString(summary))
                    .set(RplFullValidSubTaskDynamicSqlSupport.taskState)
                    .equalTo(ReplicaFullValidTaskState.FINISHED.toString())
                    .where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
        } catch (Exception e) {
            log.error("Failed to run check task!", e);
            summary.success = false;
            summary.info = e.toString();
            subTaskMapper.update(
                r -> r.set(RplFullValidSubTaskDynamicSqlSupport.summary).equalTo(JSON.toJSONString(summary))
                    .set(RplFullValidSubTaskDynamicSqlSupport.taskState)
                    .equalTo(ReplicaFullValidTaskState.ERROR.toString())
                    .where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
        } finally {
            MDC.remove(Constants.MDC_RPL_FULL_VALID_TASK_ID_KEY);
        }
    }

    private void check() throws Exception {
        log.info("start to check...");
        srcTableInfo = context.getSrcDbMetaCache().getTableInfo(config.getSrcDb(), config.getSrcTb());
        dstTableInfo = context.getDstDbMetaCache().getTableInfo(config.getDstDb(), config.getDstTb());
        srcDataSource = context.getSrcDbMetaCache().getDataSource(config.getSrcDb());
        dstDataSource = context.getDstDbMetaCache().getDataSource(config.getDstDb());
        srcJdbcTemplate = new JdbcTemplate(srcDataSource);
        dstJdbcTemplate = new JdbcTemplate(dstDataSource);

        List<String> pks = dstTableInfo.getPks();
        if (CollectionUtils.isEmpty(pks)) {
            log.warn("table {}.{} has no pk, will not check it.", dstTableInfo.getSchema(), dstTableInfo.getName());
            summary.success = true;
            summary.info = "skip check because it has no pk!";
            return;
        }

        if (!checkData()) {
            summary.success = false;
            summary.info = "check data failed!";
        } else {
            summary.success = true;
            summary.info = "check success!";
        }

        log.info("check finished");
    }

    private boolean checkData() throws Exception {
        if (checkHash()) {
            return true;
        }
        log.warn("check by hash failed!");

        return checkDetail();
    }

    private boolean checkHash() {
        List<Object> lowerBound = config.getLowerBound();
        List<Object> upperBound = config.getUpperBound();

        String rplHashCheckSql =
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(dstTableInfo, !lowerBound.isEmpty(),
                !upperBound.isEmpty());

        log.info("start to check hash digest. rpl hash check sql:{}", rplHashCheckSql);

        long srcDigest = getHashDigest(srcJdbcTemplate, rplHashCheckSql, lowerBound, upperBound);
        long dstDigest = getHashDigest(dstJdbcTemplate, rplHashCheckSql, lowerBound, upperBound);

        return srcDigest == dstDigest;
    }

    private boolean checkDetail() throws Exception {
        int fetchSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_CHECK_DETAIL_FETCH_SIZE);
        final long maxCount = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_PERSIST_ROWS_COUNT);

        List<Object> lowerBound = config.getLowerBound();
        List<Object> upperBound = config.getUpperBound();

        String checkSumSql =
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(dstTableInfo, !lowerBound.isEmpty(),
                !upperBound.isEmpty());
        log.info("start to check detail info. check sql:{}", checkSumSql);

        List<ReplicaFullValidDiffInfo> diffInfos = new ArrayList<>();
        try (Connection srcConn = srcDataSource.getConnection();
            Connection dstConn = dstDataSource.getConnection();
            PreparedStatement srcStmt = srcConn.prepareStatement(checkSumSql);
            PreparedStatement dstStmt = dstConn.prepareStatement(checkSumSql);) {
            srcStmt.setFetchSize(fetchSize);
            dstStmt.setFetchSize(fetchSize);
            Object[] params = ArrayUtils.addAll(lowerBound.toArray(), upperBound.toArray());
            for (int i = 0; i < params.length; i++) {
                srcStmt.setObject(i + 1, params[i]);
                dstStmt.setObject(i + 1, params[i]);
            }

            try (ResultSet srcRs = srcStmt.executeQuery();
                ResultSet dstRs = dstStmt.executeQuery()) {
                List<String> pkNames = dstTableInfo.getKeyList();
                // TODO: use object
                List<String> srcPkVal = null;
                List<String> dstPkVal = null;

                while (srcRs.next() && dstRs.next()) {
                    String srcCheckSum = srcRs.getString("checksum");
                    String dstCheckSum = dstRs.getString("checksum");
                    if (srcCheckSum.equals(dstCheckSum)) {
                        continue;
                    }

                    srcPkVal = new ArrayList<>();
                    dstPkVal = new ArrayList<>();
                    for (String pkName : pkNames) {
                        srcPkVal.add(srcRs.getString(pkName));
                        dstPkVal.add(dstRs.getString(pkName));
                    }

                    int cmp = comparePk(srcPkVal, dstPkVal);
                    ReplicaFullValidDiffInfo diff;
                    if (cmp == 0) {
                        summary.diff++;
                        diff = new ReplicaFullValidDiffInfo(srcPkVal, dstPkVal, "Diff");
                    } else if (cmp > 0) {
                        summary.orphan++;
                        diff = new ReplicaFullValidDiffInfo(null, dstPkVal, "Orphan");
                        srcRs.previous();
                    } else {
                        summary.miss++;
                        diff = new ReplicaFullValidDiffInfo(srcPkVal, null, "Miss");
                        dstRs.previous();
                    }

                    if (diffInfos.size() < maxCount) {
                        diffInfos.add(diff);
                    }
                }

                while (srcRs.next()) {
                    summary.miss++;
                    if (diffInfos.size() < maxCount) {
                        srcPkVal = new ArrayList<>();
                        for (String pkName : pkNames) {
                            srcPkVal.add(srcRs.getString(pkName));
                        }
                        diffInfos.add(new ReplicaFullValidDiffInfo(srcPkVal, null, "Miss"));
                    }
                }

                while (dstRs.next()) {
                    summary.orphan++;
                    if (diffInfos.size() < maxCount) {
                        dstPkVal = new ArrayList<>();
                        for (String pkName : pkNames) {
                            dstPkVal.add(dstRs.getString(pkName));
                        }
                        diffInfos.add(new ReplicaFullValidDiffInfo(null, dstPkVal, "Orphan"));
                    }
                }
            }
        }

        log.info("check detail finished. diff:{}, orphan:{}, miss:{}", summary.diff, summary.orphan, summary.miss);

        if (diffInfos.isEmpty()) {
            log.info("No diff rows!");
            return true;
        } else if (diffInfos.size() < maxCount) {
            persistDiffRows(diffInfos);
            return false;
        } else {
            log.warn("Too many diff rows, will skip persist diff!");
            return false;
        }
    }

    private int comparePk(List<String> pkVal1, List<String> pkVal2) {
        int result = 0;
        for (int i = 0; i < pkVal1.size(); i++) {
            result = pkVal1.get(i).compareTo(pkVal2.get(i));
            if (result != 0) {
                return result;
            }
        }
        return result;
    }

    private long getHashDigest(JdbcTemplate jdbcTemplate, String rplHashCheckSql,
                               List<Object> lowerBound, List<Object> upperBound) {
        Object[] params = ArrayUtils.addAll(lowerBound.toArray(), upperBound.toArray());
        List<Long> res = jdbcTemplate.query(rplHashCheckSql, (rs, rowNum) -> rs.getLong("HASH"), params);
        return res.get(0);
    }

    private void persistDiffRows(List<ReplicaFullValidDiffInfo> diffInfos) throws Exception {
        log.info("Begin to persist diff rows. Table:{}.{}, Diff number:{}", config.getSrcDb(), config.getSrcTb(),
            diffInfos.size());
        List<RplFullValidDiff> diffList = new ArrayList<>();
        String srcKeyName =
            context.getSrcDbMetaCache().getTableInfo(config.getSrcDb(), config.getSrcTb()).getKeyList().toString();
        String dstKeyName =
            context.getDstDbMetaCache().getTableInfo(config.getDstDb(), config.getDstTb()).getKeyList().toString();
        for (int i = 0; i < diffInfos.size(); i++) {
            ReplicaFullValidDiffInfo info = diffInfos.get(i);
            RplFullValidDiff diff = new RplFullValidDiff();
            diff.setTaskId(context.getFullValidTaskId());
            diff.setSrcLogicalDb(config.getSrcDb());
            diff.setSrcLogicalTable(config.getSrcTb());
            diff.setDstLogicalDb(config.getDstDb());
            diff.setDstLogicalTable(config.getDstTb());
            diff.setSrcKeyName(srcKeyName);
            diff.setDstKeyName(dstKeyName);
            diff.setStatus(ReplicaFullValidDiffStatus.FOUND.name());
            Date date = new Date();
            diff.setCreateTime(date);
            diff.setUpdateTime(date);
            if (info.getSrcKeyVal() != null) {
                diff.setSrcKeyVal(info.getSrcKeyVal().toString());
            }
            if (info.getDstKeyVal() != null) {
                diff.setDstKeyVal(info.getDstKeyVal().toString());
            }
            diff.setErrorType(info.getErrorType());
            diffList.add(diff);
            try {
                if (diffList.size() >= 1024 || i == diffInfos.size() - 1) {
                    log.info("Try inserting batch records. Records size:{}", diffList.size());
                    diffMapper.insertMultiple(diffList);
                    diffList = new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("Failed to do batch insert, will try to insert one by one.", e);
                diffList.forEach(r -> {
                    log.info("insert one by one for diff record:{}", r);
                    diffMapper.insertSelective(r);
                });
                diffList = new ArrayList<>();
            }
        }
    }

    public static RplFullValidSubTask generateTaskMeta(long fsmId, long fullValidTaskId, TaskConfig config) {
        RplFullValidSubTask res = new RplFullValidSubTask();
        res.setStateMachineId(fsmId);
        res.setTaskId(fullValidTaskId);
        res.setTaskConfig(JSON.toJSONString(config));
        res.setTaskType(ReplicaFullValidCheckTask.class.getName());
        res.setTaskStage(ReplicaFullValidTaskStage.CHECK.toString());
        res.setTaskState(ReplicaFullValidTaskState.READY.toString());
        return res;
    }

    @Data
    @ToString
    @AllArgsConstructor
    public static class TaskConfig {
        String srcDb;
        String srcTb;
        String dstDb;
        String dstTb;
        List<Object> lowerBound;
        List<Object> upperBound;
    }

    @Data
    @ToString
    public static class TaskSummary {
        boolean success;
        String info;
        int diff;
        int miss;
        int orphan;
    }

    @Data
    @ToString
    @AllArgsConstructor
    private static class ReplicaFullValidDiffInfo {
        List<String> srcKeyVal;
        List<String> dstKeyVal;
        String errorType;
    }

}
