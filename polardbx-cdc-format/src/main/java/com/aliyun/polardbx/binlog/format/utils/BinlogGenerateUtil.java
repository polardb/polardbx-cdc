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
package com.aliyun.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ISearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV2;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.GcnEventBuilder;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.RotateEventBuilder;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.SequenceEventBuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.XAPrepareEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;

public class BinlogGenerateUtil {

    private static AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);
    public int limit;
    protected boolean isMySQL8 = false;
    protected boolean switchOffTso = false;
    private int serverId = 1;
    private LogDecoder decoder = null;
    private LogBuffer logBuffer = null;
    private LogContext logContext = null;
    private AtomicLong sequence = new AtomicLong(1669707543263L);
    private AtomicLong tranSeq = new AtomicLong(1L);

    public static FormatDescriptionEvent buildFormatDescriptionEvent(long serverId, String mysqlVersion) {
        FormatDescriptionEvent formatDescriptionEvent = new FormatDescriptionEvent((short) 4, mysqlVersion, serverId);
        return formatDescriptionEvent;
    }

    public void generateFDE() throws Exception {
        FormatDescriptionEvent event = com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil
            .buildFormatDescriptionEvent(1, "5.6.29-TDDL-5.x");
        event.write(output);
    }

    public void setMySQL8() {
        this.isMySQL8 = true;
    }

    public void writeMagicCode() {
        reset();
        output.put(FileLogFetcher.BINLOG_MAGIC);
    }

    public void generateQueryLogEvent(String schema, String queryLog) throws Exception {
        QueryEventBuilder ddlBuilder = new QueryEventBuilder(schema,
            queryLog,
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            true,
            getTimestamp(),
            1);
        int len = ddlBuilder.write(output);
    }

    private int getTimestamp() {
        return (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }

    public void generateXAStart(String xidUniqKey, String schema) throws Exception {
        generateQueryLogEvent(schema, "XA START " + xidUniqKey);
    }

    public void generateXAEnd(String xidUniqKey, String schema) throws Exception {
        generateQueryLogEvent(schema, "XA END " + xidUniqKey);
    }

    public void generateXACommit(String xidUniqKey, String schema) throws Exception {
        generateQueryLogEvent(schema, "XA COMMIT " + xidUniqKey);
    }

    public void generateRotate(String nextFileName) throws Exception {
        RotateEventBuilder rotateEventBuilder = new RotateEventBuilder(getTimestamp(), serverId, nextFileName, 0);
        rotateEventBuilder.write(output);
    }

    public void generateXAPrepare(String xidUniqKey) throws Exception {
        byte[] data = xidUniqKey.getBytes();
        XAPrepareEventBuilder prepareEventBuilder =
            new XAPrepareEventBuilder(getTimestamp(), serverId, false, 1, 0, data.length, data);
        prepareEventBuilder.write(output);
    }

    public void generateWriteLog(TableData td) throws Exception {
        int tableId = Math.abs(Objects.hash(td.schema, td.tableName));
        TableMapEventBuilder tme = new TableMapEventBuilder(getTimestamp(),
            serverId,
            tableId,
            td.schema,
            td.tableName,
            "utf8");
        List<Field> fieldList = new ArrayList<>();

        for (int i = 0; i < td.columnTypeList.size(); i++) {
            List<String> dataList = td.dataList.get(0);
            fieldList
                .add(MakeFieldFactory.makeField(td.columnTypeList.get(i), dataList.get(i), "utf8",
                    true, false));
        }
        tme.setFieldList(fieldList);
        tme.write(output);

        for (int r = 1; r < td.dataList.size(); r++) {
            List<String> dataList = td.dataList.get(r);
            for (int i = 0; i < td.columnTypeList.size(); i++) {
                fieldList
                    .add(MakeFieldFactory.makeField(td.columnTypeList.get(i), dataList.get(i), "utf8",
                        true, false));
            }
        }

        RowEventBuilder reb = new RowEventBuilder(tme.getTableId(),
            fieldList.size(),
            BinlogEventType.WRITE_ROWS_EVENT,
            getTimestamp(),
            serverId);
        reb.setColumnCount(fieldList.size());
        reb.setColumnsBitMap(new BitMap(fieldList.size(), true));
        RowData rowData = new RowData();
        List<Field> biFieldList = new ArrayList<>();
        BitMap nullBitmap = new BitMap(fieldList.size());
        for (int i = 0; i < fieldList.size(); i++) {
            Field f = fieldList.get(i);
            nullBitmap.set(i, f.isNull());
            if (!f.isNull()) {
                biFieldList.add(f);
            }
        }

        rowData.setBiFieldList(biFieldList);

        rowData.setBiNullBitMap(nullBitmap);
        reb.addRowData(rowData);
        reb.write(output);
    }

    public void generateSnapSequence(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        SequenceEventBuilder sequenceEventBuilder =
            new SequenceEventBuilder(getTimestamp(),
                SequenceLogEvent.ENUM_SEQUENCE_TYPE.SNAPSHOT_SEQUENCE.ordinal(), serverId, tso);
        sequenceEventBuilder.write(output);
    }

    public void generateCommitSequence(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        SequenceEventBuilder sequenceEventBuilder =
            new SequenceEventBuilder(getTimestamp(),
                SequenceLogEvent.ENUM_SEQUENCE_TYPE.COMMIT_SEQUENCE.ordinal(), serverId, tso);
        sequenceEventBuilder.write(output);
    }

    public void generateSnapGcn(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        GcnEventBuilder sequenceEventBuilder =
            new GcnEventBuilder(getTimestamp(),
                2, serverId, tso);
        sequenceEventBuilder.write(output);
    }

    public void generateCommitGcn(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        GcnEventBuilder sequenceEventBuilder =
            new GcnEventBuilder(getTimestamp(),
                0x00000004, serverId, tso);
        sequenceEventBuilder.write(output);
    }

    public LogEvent nextEvent() throws IOException {
        if (decoder == null) {
            decoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
            byte[] data = output.toBytes();
            this.limit = output.position();
            logBuffer = new LogBuffer(data, 4, limit);
            logContext = new LogContext(FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_4_0_x);
            logContext.setLogPosition(new LogPosition("", 0));
            ServerCharactorSet serverCharactorSet = new ServerCharactorSet();
            serverCharactorSet.setCharacterSetServer("utf8");
            serverCharactorSet.setCharacterSetClient("utf8");
            serverCharactorSet.setCharacterSetConnection("utf8");
            serverCharactorSet.setCharacterSetDatabase("utf8");
            logContext.setServerCharactorSet(serverCharactorSet);
            output = null;
        }
        return decoder.decode(logBuffer, logContext);
    }

    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public long nextTran() {
        return tranSeq.incrementAndGet();
    }

    public long localTransaction() throws Exception {
        long begin = output.position();
        generateQueryLogEvent("test_phy_db_000000", "BEGIN");
        TableData tdh = new TableData("test_phy_db_000000", "__test_heart___89vr");
        tdh.setColumnTypeList(Arrays.asList("bigint(20)", "varchar(10)", "datetime(3)"));
        tdh.addRow(Arrays.asList("600002", "heartbeat",
            DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss")));
        tdh.addRow(Arrays.asList("600003", "heartbeat",
            DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss")));
        generateQueryLogEvent("test_phy_db_000000", "COMMIT");
        return begin;
    }

    public void flush(String dstFile) throws Exception {
        FileOutputStream fos = new FileOutputStream(dstFile);
        fos.write(output.toBytes(), 0, output.size());
        fos.flush();
        fos.close();
    }

    public BinlogPosition search(long searchTSO) throws Exception {
        String binlogFileName = "mysql-bin.00001";
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(null, null, null, "utf8");
        ISearchTsoEventHandle searchTsoEventHandle =
            new SearchTsoEventHandleV2(authenticationInfo, searchTSO, searchTSO, false);
        searchTsoEventHandle.onStart();
        searchTsoEventHandle.setEndPosition(new BinlogPosition(binlogFileName, 1024 * 8, 0, 0));
        LogPosition logPosition = new LogPosition(binlogFileName, 4);
        while (true) {
            LogEvent le = nextEvent();
            searchTsoEventHandle.handle(le, logPosition);
            if (le instanceof RotateLogEvent) {
                System.out.println("occur end");
                break;
            }
            if (searchTsoEventHandle.searchResult() != null) {
                System.out.println("search result");
                break;
            }
        }
        return searchTsoEventHandle.searchResult();
    }

    public void reset() {
        output = new AutoExpandBuffer(1024, 1024);
        decoder = null;
    }

    public static class TableData {
        private String schema;
        private String tableName;
        private List<String> columnTypeList;
        private List<List<String>> dataList = Lists.newArrayList();

        public TableData(String schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        public void addRow(List<String> data) {
            if (columnTypeList == null && columnTypeList.size() != data.size()) {
                throw new PolardbxException("table data define error!");
            }
            dataList.add(data);
        }

        public void setColumnTypeList(List<String> columnTypeList) {
            this.columnTypeList = columnTypeList;
        }
    }

    public class CdcStart extends XATransaction {

        public CdcStart() {
            super("__cdc___000000", "GROUP_XXX_1", "__cdc_instruction___q4oc");
        }

        @Override
        protected List<TableData> prepareLogData() {
            TableData tdh = new TableData("__cdc___000000", "__cdc_instruction___q4oc");
            tdh.setColumnTypeList(Arrays.asList("bigint(20)", "varchar(50)", "mediumtext", "timestamp", "varchar(50)"));
            tdh.addRow(Arrays.asList("600002", "CdcStart", "{}",
                DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"), "BINLOG_group_dev_000000"));
            return Lists.newArrayList(tdh);
        }

    }

    public abstract class XATransaction {
        private String xid;
        private long tso = -1;
        private long begin = -1;
        private String schemaName;
        private String groupName;
        private String tableName;

        public XATransaction(String schemaName, String groupName, String tableName) {
            this.schemaName = schemaName;
            this.groupName = groupName;
            this.tableName = tableName;
        }

        public long getBegin() {
            return begin;
        }

        public long getTso() {
            return tso;
        }

        protected abstract List<TableData> prepareLogData();

        public void prepare() throws Exception {
            this.xid = LogEventUtil.makeXid(nextTran(), groupName);
            if (isMySQL8) {
                generateSnapGcn(nextSequence());
            }
            begin = output.position();
            generateXAStart(xid, schemaName);
            if (!isMySQL8) {
                generateSnapSequence(nextSequence());
            }
            List<TableData> tableDataList = prepareLogData();
            for (TableData td : tableDataList) {
                generateWriteLog(td);
            }
            generateXAEnd(xid, schemaName);
            generateXAPrepare(xid);
        }

        public void preCommit() {
            this.tso = nextSequence();
        }

        public void commit() throws Exception {
            if (this.tso == -1) {
                this.tso = nextSequence();
            }
            if (isMySQL8) {
                generateCommitGcn(tso);
            } else {
                generateCommitSequence(tso);
            }
            generateXACommit(this.xid, schemaName);
        }
    }

    public class Heartbeat extends XATransaction {

        public Heartbeat() {
            super("__cdc___000000", "GROUP_XXX_1", "__cdc_heartbeat___89vr");
        }

        @Override
        protected List<TableData> prepareLogData() {
            TableData tdh = new TableData("__cdc___000000", "__cdc_heartbeat___89vr");
            tdh.setColumnTypeList(Arrays.asList("bigint(20)", "varchar(10)", "datetime(3)"));
            tdh.addRow(Arrays.asList("600002", "heartbeat",
                DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss")));
            return Lists.newArrayList(tdh);
        }
    }

    public static int getTableIdLength() {
        if (FormatDescriptionEvent.EVENT_HEADER_LENGTH[TABLE_MAP_EVENT - 1] == 6) {
            return 4;
        } else {
            return 6;
        }
    }
}
