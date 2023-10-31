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
package com.aliyun.polardbx.binlog.format.utils.generator;

import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.GcnEventBuilder;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.RotateEventBuilder;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.RowsQueryEventBuilder;
import com.aliyun.polardbx.binlog.format.SequenceEventBuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.XAPrepareEventBuilder;
import com.aliyun.polardbx.binlog.format.XidEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;

public class BinlogGenerateUtil {

    protected AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);
    public int limit;
    protected boolean switchOffTso = false;
    protected final int serverId = 1;
    protected LogDecoder decoder = null;
    protected LogBuffer logBuffer = null;
    protected LogContext logContext = null;
    protected final AtomicLong sequence = new AtomicLong(1669707543263L);
    protected final AtomicLong tranSeq = new AtomicLong(1L);

    protected final boolean mysql8;

    protected Transaction currentTransaction;

    private final AtomicInteger atomicLong = new AtomicInteger(0);

    private final VirtualFile virtualFile = new VirtualFile();
    private PipedOutputStream outputStream;
    private PipedInputStream inputStream;

    private class VirtualFile {
        String fileName;
        long offset;
    }

    private final LoadingCache<String, Integer> tableIdMap =
        CacheBuilder.newBuilder().build(new CacheLoader<String, Integer>() {
            @Override
            public Integer load(String key) {
                return atomicLong.getAndIncrement();
            }
        });

    public BinlogGenerateUtil(boolean mysql8) {
        this.mysql8 = mysql8;
        try {
            outputStream = new PipedOutputStream();
            inputStream = new PipedInputStream(1024 * 1024 * 5);
            outputStream.connect(inputStream);
//            writeMagicCode();
            generateFDE();
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    public static FormatDescriptionEvent buildFormatDescriptionEvent(long serverId, String mysqlVersion) {
        return new FormatDescriptionEvent((short) 4, mysqlVersion, serverId);
    }

    public static int getTableIdLength() {
        if (FormatDescriptionEvent.EVENT_HEADER_LENGTH[TABLE_MAP_EVENT - 1] == 6) {
            return 4;
        } else {
            return 6;
        }
    }

    private void doFlush() {
        byte[] data = output.toBytes();
        int size = output.size();
        virtualFile.offset += size;
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(13);
        byteArray.writeLong(virtualFile.offset, 4);
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, size - LogEvent.BINLOG_CHECKSUM_LEN);
        byteArray = new ByteArray(data);
        byteArray.skip(size - LogEvent.BINLOG_CHECKSUM_LEN);
        byteArray.writeLong(crc32.getValue(), LogEvent.BINLOG_CHECKSUM_LEN);
        try {
            outputStream.write(data, 0, size);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        output.reset();
    }

    private void generateFDE() throws Exception {
        FormatDescriptionEvent event = BinlogGenerateUtil
            .buildFormatDescriptionEvent(1, "5.6.29-TDDL-5.x");
        event.write(output);
        ByteArray byteArray = new ByteArray(output.toBytes());
        byteArray.skip(13);
        byteArray.writeLong(virtualFile.offset, 4);
        outputStream.write(output.toBytes(), 0, output.size());
        output.reset();
    }

    private void writeMagicCode() throws IOException {
        reset();
        outputStream.write(FileLogFetcher.BINLOG_MAGIC);
    }

    public void generateXidLogEvent() throws Exception {
        XidEventBuilder xidEventBuilder = new XidEventBuilder(getTimestamp(), serverId, xidSequence.getAndIncrement());
        xidEventBuilder.write(output);
        doFlush();
    }

    private AtomicInteger xidSequence = new AtomicInteger(0);

    public void generateQueryLogEvent(String schema, String queryLog) throws Exception {
        QueryEventBuilder ddlBuilder = new QueryEventBuilder(schema,
            queryLog,
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            true,
            getTimestamp(),
            1,
            0);
        ddlBuilder.write(output);
        doFlush();
    }

    public void close() {
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Cts writeCTS(long tranId) throws Exception {
        long seq = nextSequence();
        String tso = generateCts(seq, tranId);
        String cts = MarkType.CTS + "::" + tso;
        generateRowsQueryLog(cts);
        return new Cts(tso, virtualFile.offset);
    }

    protected int getTimestamp() {
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

    public long generateRotate(String nextFileName) throws Exception {
        RotateEventBuilder rotateEventBuilder = new RotateEventBuilder(getTimestamp(), serverId, nextFileName, 0);
        rotateEventBuilder.write(output);
        doFlush();
        virtualFile.fileName = nextFileName;
        return virtualFile.offset;
    }

    public void generateRowsQueryLog(String rowsQueryLog) throws Exception {
        RowsQueryEventBuilder rotateEventBuilder = new RowsQueryEventBuilder(getTimestamp(), serverId, rowsQueryLog);
        rotateEventBuilder.write(output);
        doFlush();
    }

    public void generateXAPrepare(String xidUniqKey) throws Exception {
        byte[] data = xidUniqKey.getBytes();
        XAPrepareEventBuilder prepareEventBuilder =
            new XAPrepareEventBuilder(getTimestamp(), serverId, false, 1, 0, data.length, data);
        prepareEventBuilder.write(output);
        doFlush();
    }

    public void generateWriteLog(TableData td) throws Exception {
        String key = td.schema + td.tableName;
        int tableId = tableIdMap.get(key);
        TableMapEventBuilder tme = new TableMapEventBuilder(getTimestamp(),
            serverId,
            tableId,
            td.schema,
            td.tableName,
            "utf8");
        List<Field> fieldList = new ArrayList<>();
        TableDefine tableDefine = tableDefineMap.get(td.key());
        for (int i = 0; i < tableDefine.columnTypeList.size(); i++) {
            List<String> dataList = td.dataList.get(0);
            fieldList
                .add(MakeFieldFactory.makeField(tableDefine.columnTypeList.get(i), dataList.get(i), "utf8",
                    true, false));
        }
        tme.setFieldList(fieldList);
        tme.write(output);

        for (int r = 1; r < td.dataList.size(); r++) {
            List<String> dataList = td.dataList.get(r);
            for (int i = 0; i < tableDefine.columnTypeList.size(); i++) {
                fieldList
                    .add(MakeFieldFactory.makeField(tableDefine.columnTypeList.get(i), dataList.get(i), "utf8",
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
        doFlush();
    }

    public void generateSnapSequence(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        SequenceEventBuilder sequenceEventBuilder =
            new SequenceEventBuilder(getTimestamp(),
                SequenceLogEvent.ENUM_SEQUENCE_TYPE.SNAPSHOT_SEQUENCE.ordinal(), serverId, tso);
        sequenceEventBuilder.write(output);
        doFlush();
    }

    public void generateCommitSequence(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        SequenceEventBuilder sequenceEventBuilder =
            new SequenceEventBuilder(getTimestamp(),
                SequenceLogEvent.ENUM_SEQUENCE_TYPE.COMMIT_SEQUENCE.ordinal(), serverId, tso);
        sequenceEventBuilder.write(output);
        doFlush();
    }

    public void generateSnapGcn(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        GcnEventBuilder sequenceEventBuilder =
            new GcnEventBuilder(getTimestamp(),
                2, serverId, tso);
        sequenceEventBuilder.write(output);
        doFlush();
    }

    public void generateCommitGcn(long tso) throws Exception {
        if (switchOffTso) {
            return;
        }
        GcnEventBuilder sequenceEventBuilder =
            new GcnEventBuilder(getTimestamp(),
                0x00000004, serverId, tso);
        sequenceEventBuilder.write(output);
        doFlush();
    }

    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public long nextTran() {
        return tranSeq.incrementAndGet();
    }

    public void localTransaction() {
        defineTable("test_phy_db_000000", "__test_heart___89vr", "bigint(20)", "varchar(10)", "datetime(3)");
        Transaction transaction = newTransaction("test_phy_db_000000");
        addTableData("__test_heart___89vr", "600002", "heartbeat",
            DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"));
        addTableData("__test_heart___89vr", "600003", "heartbeat",
            DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"));
        transaction.commit();
    }

    public void reset() {
        output = new AutoExpandBuffer(1024, 1024);
        decoder = null;
    }

    public static class Cts {
        final String tso;
        final long position;

        public Cts(String tso, long position) {
            this.tso = tso;
            this.position = position;
        }

        public String getTso() {
            return tso;
        }

        public long getPosition() {
            return position;
        }
    }

    public static class TableDefine {
        final String schema;
        final String tableName;

        @Getter
        long position;

        @Getter
        String tso;

        final List<String> columnTypeList = new ArrayList<>();

        public TableDefine(String schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        public TableDefine(String schema, String tableName, String... types) {
            this.schema = schema;
            this.tableName = tableName;
            this.columnTypeList.addAll(Arrays.asList(types));
        }

        public String key() {
            return schema + "." + tableName;
        }
    }

    protected final Map<String, TableDefine> tableDefineMap = Maps.newHashMap();

    public TableDefine defineTable(String schema, String tableName, String... types) {
        String[] nameVsTypeList = new String[types.length * 2];
        int seq = 0;
        for (String t : types) {
            nameVsTypeList[seq] = RandomStringUtils.random(5, true, false) + "_" + seq++;
            nameVsTypeList[seq++] = t;
        }
        return defineTableWithName(schema, tableName, nameVsTypeList);
    }

    public Ddl createDatabase(String database) {
        String sql = "create database " + database;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("# POLARX_ORIGIN_SQL=").append(sql).append("\n");
        long seq = nextSequence();
        String tso = generateCts(seq, 0);
        stringBuilder.append("# POLARX_TSO=").append(tso).append("\n");
        stringBuilder.append(sql);
        try {
            generateQueryLogEvent("", stringBuilder.toString());
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        return new Ddl(virtualFile.offset, tso);
    }

    public TableDefine defineTableWithName(String schema, String tableName, String... types) {
        String[] colTypes = new String[types.length / 2];
        for (int i = 0; i < types.length; ) {
            colTypes[i / 2] = types[i + 1];
            i += 2;
        }
        TableDefine define = new TableDefine(schema, tableName, colTypes);
        if (tableDefineMap.put(define.key(), define) != null) {
            return tableDefineMap.get(define.key());
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("create table ").append(tableName).append("(");
        for (int i = 0; i < types.length; i++) {
            sqlBuilder.append(types[i++]).append("\t");
            sqlBuilder.append(types[i]);
            if (i + 1 < types.length) {
                sqlBuilder.append(",");
            }
        }
        String createSql = sqlBuilder.toString();
        StringBuilder queryLogBuilder = new StringBuilder();
        queryLogBuilder.append("# POLARX_ORIGIN_SQL=").append(createSql).append("\n");
        long seq = nextSequence();
        String tso = generateCts(seq, 0);
        queryLogBuilder.append("# POLARX_TSO=").append(tso).append("\n");
        queryLogBuilder.append(createSql);
        try {
            generateQueryLogEvent(schema, queryLogBuilder.toString());
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
        define.position = virtualFile.offset;
        define.tso = tso;
        return define;
    }

    private String generateCts(long seq, long tran) {
        return CommonUtils.generateTSO(seq, StringUtils.leftPad(tran + "", 19, "0"), null);
    }

    public static class Ddl {
        private long offset;
        private String tso;

        public Ddl(long offset, String tso) {
            this.offset = offset;
            this.tso = tso;
        }

        public long getOffset() {
            return offset;
        }

        public String getTso() {
            return tso;
        }
    }

    public class TableData {
        protected final String schema;
        protected final String tableName;
        protected final List<List<String>> dataList = Lists.newArrayList();

        public TableData(String schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        protected String key() {
            return schema + "." + tableName;
        }

        public void addRow(List<String> data) {
            TableDefine tableDefine = tableDefineMap.get(key());
            if (tableDefine == null || tableDefine.columnTypeList.size() != data.size()) {
                throw new PolardbxException("table data define error!");
            }
            dataList.add(data);
        }
    }

    public Transaction newTransaction(String schemaName, String groupName, boolean xa)
        throws UnsupportedEncodingException {
        currentTransaction = new Transaction(schemaName, groupName, xa);
        return currentTransaction;
    }

    public Transaction newTransaction(String schemaName) {
        currentTransaction = new Transaction(schemaName);
        return currentTransaction;
    }

    public void addTableData(String tableName, String... data) {
        currentTransaction.addTableData(tableName, data);
    }

    public class Transaction {
        String schemaName;
        String groupName;

        @Getter
        boolean xa;

        @Getter
        long tranId;

        @Getter
        String xid;
        List<TableData> tableDataList = Lists.newArrayList();
        TableData td;

        boolean prepare;

        @Getter
        protected long tso = -1;

        @Getter
        protected long begin = -1;

        public void prepare() throws Exception {
            if (xa) {
                if (begin == -1) {
                    begin();
                }
                write();
                generateXAEnd(xid, schemaName);
                generateXAPrepare(xid);
                this.prepare = true;
            }
        }

        public Transaction(String schemaName, String groupName, boolean xa) {
            this.schemaName = schemaName;
            this.groupName = groupName;
            this.xa = xa;
            this.tranId = nextTran();
            try {
                this.xid = LogEventUtil.makeXid(tranId, groupName);
            } catch (Exception e) {
                throw new PolardbxException(e);
            }
        }

        public Transaction(String schemaName) {
            this.schemaName = schemaName;
        }

        public void addTableData(String tableName, String... data) {
            if (td == null || !StringUtils.equals(td.tableName, tableName)) {
                td = new TableData(schemaName, tableName);
                this.tableDataList.add(td);
            }
            td.addRow(Lists.newArrayList(data));
        }

        public void begin() {
            try {
                if (xa) {
                    if (mysql8) {
                        generateSnapGcn(nextSequence());
                    }
                    generateXAStart(xid, schemaName);
                    begin = virtualFile.offset;
                    if (!mysql8) {
                        generateSnapSequence(nextSequence());
                    }
                } else {
                    generateQueryLogEvent(schemaName, "BEGIN");
                    begin = virtualFile.offset;
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        protected void write() throws Exception {
            for (TableData td : tableDataList) {
                generateWriteLog(td);
            }
        }

        public void tryPreGenerateTso() {
            if (this.tso == -1) {
                this.tso = nextSequence();
            }
        }

        public void commit() {
            try {
                if (xa && !prepare) {
                    prepare();
                }

                if (!xa) {
                    if (begin == -1) {
                        begin();
                    }
                    write();
                }

                if (xa) {
                    tryPreGenerateTso();
                    if (mysql8) {
                        generateCommitGcn(tso);
                    } else {
                        generateCommitSequence(tso);
                    }
                    generateXACommit(xid, schemaName);
                } else {
                    generateXidLogEvent();
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public LogFetcher logFetcher() {

        final InputStream fin = inputStream;
        return new LogFetcher() {

            @Override
            public boolean fetch() throws IOException {
                if (limit == 0) {
                    final int len = fin.read(buffer, 0, buffer.length);
                    if (len >= 0) {
                        limit += len;
                        position = 0;
                        origin = 0;

                        /* More binlog to fetch */
                        return true;
                    }
                } else if (origin == 0) {
                    if (limit > buffer.length / 2) {
                        ensureCapacity(buffer.length + limit);
                    }
                    final int len = fin.read(buffer, limit, buffer.length - limit);
                    if (len >= 0) {
                        limit += len;

                        /* More binlog to fetch */
                        return true;
                    }
                } else if (limit > 0) {
                    if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
                        int lenPosition = position + 4 + 1 + 4;
                        long eventLen =
                            ((long) (0xff & buffer[lenPosition++])) | ((long) (0xff & buffer[lenPosition++]) << 8)
                                | ((long) (0xff & buffer[lenPosition++]) << 16)
                                | ((long) (0xff & buffer[lenPosition++]) << 24);

                        if (limit >= eventLen) {
                            return true;
                        } else {
                            ensureCapacity((int) eventLen);
                        }
                    }

                    System.arraycopy(buffer, origin, buffer, 0, limit);
                    position -= origin;
                    origin = 0;
                    final int len = fin.read(buffer, limit, buffer.length - limit);
                    if (len >= 0) {
                        limit += len;

                        /* More binlog to fetch */
                        return true;
                    }
                } else {
                    /* Should not happen. */
                    throw new IllegalArgumentException("Unexcepted limit: " + limit);
                }

                /* Reach binlog file end */
                return false;
            }

            @Override
            public void close() throws IOException {
                fin.close();
            }

        };

    }

}
