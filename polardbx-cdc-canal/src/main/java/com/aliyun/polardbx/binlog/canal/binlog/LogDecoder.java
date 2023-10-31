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
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.IBinlogFileInfoFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.event.AppendBlockLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.BeginLoadQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.CreateFileLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteFileLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.ExecuteLoadLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.ExecuteLoadQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GtidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.HeartbeatLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IgnorableLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IncidentLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LoadLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.PreviousGtidsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.StartLogEventV3;
import com.aliyun.polardbx.binlog.canal.binlog.event.StopLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TransactionContextLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.BinlogCheckPointLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.MariaGtidListLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.MariaGtidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.StartEncryptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.BitSet;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS;

/**
 * Implements a binary-log decoder.
 *
 * <pre>
 * LogDecoder decoder = new LogDecoder();
 * decoder.handle(...);
 *
 * LogEvent event;
 * do
 * {
 *     event = decoder.decode(buffer, context);
 *
 *     // process log event.
 * }
 * while (event != null);
 * // no more events in buffer.
 * </pre>
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class LogDecoder {

    protected static final Logger logger = LoggerFactory.getLogger(LogDecoder.class);

    protected final BitSet handleSet = new BitSet(LogEvent.ENUM_END_EVENT);

    protected IBinlogFileInfoFetcher binlogFileSizeFetcher;

    protected boolean needRecordData = true;

    protected boolean needFixRotate = true;

    public LogDecoder() {
    }

    public LogDecoder(final int fromIndex, final int toIndex) {
        handleSet.set(fromIndex, toIndex);
    }

    /**
     * Deserialize an event from buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped.
     */
    public LogEvent decode(LogBuffer buffer, LogHeader header, LogContext context) throws IOException {
        FormatDescriptionLogEvent descriptionEvent = context.getFormatDescription();
        LogPosition logPosition = context.getLogPosition();

        int checksumAlg = LogEvent.BINLOG_CHECKSUM_ALG_UNDEF;
        if (header.getType() != LogEvent.FORMAT_DESCRIPTION_EVENT) {
            checksumAlg = descriptionEvent.header.getChecksumAlg();
        } else {
            // 如果是format事件自己，也需要处理checksum
            checksumAlg = header.getChecksumAlg();
        }

        if (checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_OFF && checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_UNDEF) {
            // remove checksum bytes
            buffer.limit(header.getEventLen() - LogEvent.BINLOG_CHECKSUM_LEN);
        }

        switch (header.getType()) {
        case LogEvent.SEQUENCE_EVENT: {
            SequenceLogEvent event = new SequenceLogEvent(header, buffer, descriptionEvent);
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GCN_EVENT: {
            GcnLogEvent event = new GcnLogEvent(header, buffer, descriptionEvent);
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.QUERY_EVENT: {
            QueryLogEvent event = new QueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.XID_EVENT: {
            XidLogEvent event = new XidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.TABLE_MAP_EVENT: {
            ServerCharactorSet charactorSet = context.getServerCharactorSet();
            TableMapLogEvent mapEvent = new TableMapLogEvent(header, buffer, descriptionEvent,
                CharsetConversion.getJavaCharset(charactorSet.getCharacterSetServer()));
            /* updating position in context */
            logPosition.position = header.getLogPos();
            context.putTable(mapEvent);
            return mapEvent;
        }
        case LogEvent.WRITE_ROWS_EVENT_V1: {
            RowsLogEvent event = new WriteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.UPDATE_ROWS_EVENT_V1: {
            RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.DELETE_ROWS_EVENT_V1: {
            RowsLogEvent event = new DeleteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.ROTATE_EVENT: {
            RotateLogEvent event = new RotateLogEvent(header, buffer, descriptionEvent);
            if (needFixRotate) {
                event = tryFixRotateEvent(event, logPosition);
            }
            /* updating position in context */
            logPosition = new LogPosition(event.getFilename(), event.getPosition());
            context.setLogPosition(logPosition);
            return event;
        }
        case LogEvent.LOAD_EVENT:
        case LogEvent.NEW_LOAD_EVENT: {
            LoadLogEvent event = new LoadLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.SLAVE_EVENT: /* can never happen (unused event) */ {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported SLAVE_EVENT from: "
                    + context.getLogPosition());
            }
            break;
        }
        case LogEvent.CREATE_FILE_EVENT: {
            CreateFileLogEvent event = new CreateFileLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.APPEND_BLOCK_EVENT: {
            AppendBlockLogEvent event = new AppendBlockLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.DELETE_FILE_EVENT: {
            DeleteFileLogEvent event = new DeleteFileLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.EXEC_LOAD_EVENT: {
            ExecuteLoadLogEvent event = new ExecuteLoadLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.START_EVENT_V3: {
            /* This is sent only by MySQL <=4.x */
            StartLogEventV3 event = new StartLogEventV3(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.STOP_EVENT: {
            StopLogEvent event = new StopLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.INTVAR_EVENT: {
            IntvarLogEvent event = new IntvarLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.RAND_EVENT: {
            RandLogEvent event = new RandLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.USER_VAR_EVENT: {
            UserVarLogEvent event = new UserVarLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.FORMAT_DESCRIPTION_EVENT: {
            descriptionEvent = new FormatDescriptionLogEvent(header, buffer, descriptionEvent);
            context.setFormatDescription(descriptionEvent);
            return descriptionEvent;
        }
        case LogEvent.PRE_GA_WRITE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_WRITE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Write_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.PRE_GA_UPDATE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_UPDATE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Update_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.PRE_GA_DELETE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_DELETE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Delete_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.BEGIN_LOAD_QUERY_EVENT: {
            BeginLoadQueryLogEvent event = new BeginLoadQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.EXECUTE_LOAD_QUERY_EVENT: {
            ExecuteLoadQueryLogEvent event = new ExecuteLoadQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.INCIDENT_EVENT: {
            IncidentLogEvent event = new IncidentLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.HEARTBEAT_LOG_EVENT: {
            HeartbeatLogEvent event = new HeartbeatLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.IGNORABLE_LOG_EVENT: {
            IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.ROWS_QUERY_LOG_EVENT: {
            RowsQueryLogEvent event = new RowsQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.WRITE_ROWS_EVENT: {
            RowsLogEvent event = new WriteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.UPDATE_ROWS_EVENT: {
            RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.DELETE_ROWS_EVENT: {
            RowsLogEvent event = new DeleteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.GTID_LOG_EVENT:
        case LogEvent.ANONYMOUS_GTID_LOG_EVENT: {
            GtidLogEvent event = new GtidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.PREVIOUS_GTIDS_LOG_EVENT: {
            PreviousGtidsLogEvent event = new PreviousGtidsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.TRANSACTION_CONTEXT_EVENT: {
            TransactionContextLogEvent event = new TransactionContextLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        // case LogEvent.VIEW_CHANGE_EVENT: {
        // ViewChangeEvent event = new ViewChangeEvent(header, buffer,
        // descriptionEvent);
        // /* updating position in context */
        // logPosition.position = header.getLogPos();
        // return event;
        // }
        case LogEvent.XA_PREPARE_LOG_EVENT: {
            XaPrepareLogEvent event = new XaPrepareLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.ANNOTATE_ROWS_EVENT: {
            AnnotateRowsEvent event = new AnnotateRowsEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.BINLOG_CHECKPOINT_EVENT: {
            BinlogCheckPointLogEvent event = new BinlogCheckPointLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GTID_EVENT: {
            MariaGtidLogEvent event = new MariaGtidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GTID_LIST_EVENT: {
            MariaGtidListLogEvent event = new MariaGtidListLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.START_ENCRYPTION_EVENT: {
            StartEncryptionLogEvent event = new StartEncryptionLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }

        default:
            /*
             * Create an object of Ignorable_log_event for unrecognized sub-class. So that SLAVE SQL THREAD will
             * only update the position and continue.
             */
            if ((buffer.getUint16(LogEvent.FLAGS_OFFSET) & LogEvent.LOG_EVENT_IGNORABLE_F) > 0) {
                IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unrecognized binlog event " + LogEvent.getTypeName(header.getType())
                        + " from: " + context.getLogPosition());
                }
            }
        }

        /* updating position in context */
        logPosition.position = header.getLogPos();
        /* Unknown or unsupported log event */
        return new UnknownLogEvent(header);
    }

    public final void handle(final int fromIndex, final int toIndex) {
        handleSet.set(fromIndex, toIndex);
    }

    public final void handle(final int flagIndex) {
        handleSet.set(flagIndex);
    }

    /**
     * Decoding an event from binary-log buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped,
     * <code>null</code> if buffer is not including a full event.
     */
    public LogEvent decode(LogBuffer buffer, LogContext context) throws IOException {
        final int limit = buffer.limit();

        if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
            LogHeader header = new LogHeader(buffer, context.getFormatDescription());

            final int len = header.getEventLen();
            if (limit >= len) {
                LogEvent event;
                header.processCheckSum(buffer);
                if (needRecordData) {
                    header.initDataBuffer(buffer);
                }
                /* Checking binary-log's header */
                if (handleSet.get(header.getType())) {
                    buffer.limit(len);
                    try {
                        /* Decoding binary-log to event */
                        event = decode(buffer, header, context);
                    } catch (IOException e) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Decoding " + LogEvent.getTypeName(header.getType()) + " failed from: "
                                    + context.getLogPosition(),
                                e);
                        }
                        throw e;
                    } finally {
                        /* Restore limit */
                        buffer.limit(limit);
                    }
                } else {
                    /* Ignore unsupported binary-log. */
//                    event = new UnknownLogEvent(header);
                    event = null;
                }

                /* consume this binary-log. */
                buffer.consume(len);
                return event;
            }
        }

        /* Rewind buffer's position to 0. */
        buffer.rewind();
        return null;
    }

    private RotateLogEvent tryFixRotateEvent(RotateLogEvent event, LogPosition logPosition) throws IOException {
        if (binlogFileSizeFetcher != null && logPosition != null && StringUtils.isNotBlank(logPosition.getFileName())
            && !StringUtils.equals(logPosition.getFileName(), event.getFilename())) {
             /*
             xdb存在bug，可能会在binlog文件中的某个位置记录RotateEvent，这里做一下兼容性处理，判断rotate event是否出现在文件中
             当收到rotate event时，判断logPosition是否已经到达文件的末尾，如果logPosition.getPosition() + length(rotateEvent)之和
             小于binlog文件的size，则说明rotate event是出现在binlog文件中。但有一个例外情况，logPosition中的position字段虽然是long型，
             但在binlog文件中LogHeader实际能记录的最大值是uint32(最大值对应4G)，即logPosition的position字段的值的最大上限是uint32，
             当出现超大事务时(如20G的大事务)，logPosition.getPosition() + length(rotateEvent)之和肯定是小于binlog文件的size的，所以，
             此种情况下，就不能简单的判定rotate event是出现在binlog文件中间位置了。xdb中，可以对max_binlog_size参数设置的最大值为1073741824(1G)，
             只有出现大事务的情况下，才会导致binlog文件的大小超过max_binlog_size，所以rotate event出现在binlog文件的中间位置只会发生在小于
             max_binlog_size的位置，因此可以得出结论，当logPosition的position字段的值触达uint32阈值后，出现的rotate event一定是正常的
             rotate event，不需进行fix处理
              */
            long expectFileSize = logPosition.getPosition() + event.getEventLen();
            if (expectFileSize < Integer.MAX_VALUE) {
                long actualFileSize = getFileSize(logPosition.getFileName(), expectFileSize);
                if (expectFileSize < actualFileSize) {
                    return rebuildRotateLogEvent(event, logPosition, expectFileSize);
                }
            }
        }
        return event;
    }

    private long getFileSize(String fileName, long expectFileSize) throws IOException {
        long actualFileSize = binlogFileSizeFetcher.fetch(fileName);

        if (expectFileSize < actualFileSize) {
            //如果expectFileSize 小于文件的实际大小，则可以肯定是在文件中间位置发生了rotate，直接返回
            return actualFileSize;
        } else {
            //如果下一个binlog文件已经存在，则当前文件肯定不会再有增量写入，可以直接返回获取到的fileSize
            //如果下一个binlog文件还不存在，则等待若干个心跳时间看是否有数据写入，尽最大可能进行判断
            String nextFileName = BinlogFileUtil.getNextBinlogFileName(fileName);
            boolean isNextFileExisting = binlogFileSizeFetcher.isFileExisting(nextFileName);
            if (!isNextFileExisting) {
                logger.info("prepare to wait for newly binlog data , {}:{}", fileName, expectFileSize);
                sleep();
            }
            return binlogFileSizeFetcher.fetch(fileName);
        }
    }

    private RotateLogEvent rebuildRotateLogEvent(RotateLogEvent event, LogPosition logPosition, long expectFileSize) {
        RotateLogEvent newEvent =
            new RotateLogEvent(event.getHeader(), logPosition.getFileName(), expectFileSize);
        logger.warn("receive a invalid rotate event, will fix it, the event info before fix is :"
            + event.info() + " ,the event info after fix is :" + newEvent.info());
        return newEvent;
    }

    private void sleep() {
        try {
            int tsoHeartBeatInterval = DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL_MS);
            Thread.sleep(Math.min(tsoHeartBeatInterval * 5, 2000));
        } catch (InterruptedException e) {
        }
    }

    public void setBinlogFileSizeFetcher(IBinlogFileInfoFetcher binlogFileSizeFetcher) {
        this.binlogFileSizeFetcher = binlogFileSizeFetcher;
    }

    public void setNeedFixRotate(boolean needFixRotate) {
        this.needFixRotate = needFixRotate;
    }

    public void setNeedRecordData(boolean needRecordData) {
        this.needRecordData = needRecordData;
    }
}
