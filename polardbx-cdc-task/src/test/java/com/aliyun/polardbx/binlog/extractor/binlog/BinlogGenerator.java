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

package com.aliyun.polardbx.binlog.extractor.binlog;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.ConsoleTableMetaTSDB;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.format.BinlogBuilder;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
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
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.codec.binary.Hex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BinlogGenerator extends LogBuffer {

    private static final String ENCODING = "utf-8";
    private static FormatDescriptionLogEvent fde = new FormatDescriptionLogEvent((short) 4,
        1);
    private String tableDDL;
    private TableMeta tableMeta;
    private String db;
    private String tb;
    private long tranId = 1;
    private String groupName = "TEST_DB_00000";
    private LoadingCache<TableMeta.FieldMeta, Field> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build(new CacheLoader<TableMeta.FieldMeta, Field>() {

            @Override
            public Field load(TableMeta.FieldMeta fieldMeta) throws Exception {
                Field f = MakeFieldFactory.makeField(fieldMeta.getColumnType(),
                    fieldMeta.getDefaultValue(),
                    "utf8",
                    fieldMeta.isNullable());
                return f;
            }
        });

    private String curXid;
    private TableMapLogEvent lastTableMap;

    public BinlogGenerator(String db, String tb, String tableDDL) {
        super(new byte[1024], 0, 1024);
        this.tableDDL = tableDDL;
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null);
        memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, db, tableDDL, null);
        tableMeta = memoryTableMeta.find(db, tb);
        this.db = db;
        this.tb = tb;
    }

    public TableMeta getTableMeta() {
        return tableMeta;
    }

    private void makeXid() throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append("X'")
            .append(Hex.encodeHex((LogEventUtil.DRDS_TRAN_PREFIX + Long.toHexString(tranId) + "@1").getBytes(
                ENCODING)))
            .append("','")
            .append(Hex.encodeHex(groupName.getBytes(ENCODING)))
            .append("'");
        curXid = sb.toString();
        tranId++;
    }

    private LogHeader rebuild(BinlogBuilder binlogBuilder) throws Exception {
        AutoExpandBuffer autoExpandBuffer = new AutoExpandBuffer(1024, 1024);
        limit = binlogBuilder.write(autoExpandBuffer);
        buffer = autoExpandBuffer.toBytes();
        LogHeader logHeader = new LogHeader(this, fde);
        limit(limit - LogEvent.BINLOG_CHECKSUM_LEN);
        return logHeader;
    }

    public List<LogEvent> randomTrans() throws Exception {
        List<LogEvent> lgList = new ArrayList<>();
        lgList.add(generateXABegin());
        lgList.add(generateSNAPSequenceEvent());
        lgList.add(generateTableMap());
        lgList.add(generateRowQueryLogEvent());
        lgList.add(generateRowsLogEvent());
        lgList.add(generateTableMap());
        lgList.add(generateRowQueryLogEvent());
        lgList.add(generateRowsLogEvent());
        lgList.add(generateTableMap());
        lgList.add(generateRowQueryLogEvent());
        lgList.add(generateRowsLogEvent());
        lgList.add(generatePrepareLogEvent());
        lgList.add(generateCMTSequenceEvent());
        lgList.add(generateXACommit());
        return lgList;
    }

    public RowsQueryLogEvent generateRowQueryLogEvent() throws Exception {
        RowsQueryEventBuilder builder = new RowsQueryEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            "/*DRDS /127.0.0.1/11aaba16c1800000-2/2/XXXXXXXXXXXXXXXXXXXXXXXXX/ */");
        return new RowsQueryLogEvent(rebuild(builder), this, fde);
    }

    public FormatDescriptionLogEvent generateFDE() throws Exception {
        FormatDescriptionEvent _fde = new FormatDescriptionEvent((short) 4, "5.7.3", 1);
        fde = new FormatDescriptionLogEvent(rebuild(_fde), this, fde);
        return fde;
    }

    public QueryLogEvent generateXABegin() throws Exception {
        makeXid();
        QueryEventBuilder queryEventBuilder = new QueryEventBuilder("std",
            LogEventUtil.XA_START + " " + curXid,
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            false,
            (int) (System.currentTimeMillis() / 1000),
            1);
        return new QueryLogEvent(rebuild(queryEventBuilder), this, fde);
    }

    public QueryLogEvent generateBegin() throws Exception {
        QueryEventBuilder queryEventBuilder = new QueryEventBuilder("std",
            "BEGIN",
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            false,
            (int) (System.currentTimeMillis() / 1000),
            1);
        return new QueryLogEvent(rebuild(queryEventBuilder), this, fde);
    }

    public QueryLogEvent generateXACommit() throws Exception {
        QueryEventBuilder queryEventBuilder = new QueryEventBuilder("std",
            LogEventUtil.XA_COMMIT + " " + curXid,
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            false,
            (int) (System.currentTimeMillis() / 1000),
            1);
        return new QueryLogEvent(rebuild(queryEventBuilder), this, fde);
    }

    public XidLogEvent generateCommit() throws Exception {
        XidEventBuilder xidEventBuilder = new XidEventBuilder((int) (System.currentTimeMillis() / 1000), 1, 1);
        return new XidLogEvent(rebuild(xidEventBuilder), this, fde);
    }

    public XaPrepareLogEvent generatePrepareLogEvent() throws Exception {
        byte[] data = curXid.getBytes();
        XAPrepareEventBuilder builder = new XAPrepareEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            false,
            1,
            1,
            data.length - 1,
            data);
        return new XaPrepareLogEvent(rebuild(builder), this, fde);
    }

    public TableMapLogEvent generateTableMap() throws Exception {
        TableMapEventBuilder builder = new TableMapEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            1,
            db,
            tb);
        List<TableMeta.FieldMeta> fieldMetaList = tableMeta.getFields();
        List<Field> fieldList = new ArrayList<>();
        for (int i = 0; i < fieldMetaList.size(); i++) {
            TableMeta.FieldMeta fieldMeta = fieldMetaList.get(i);
            Field f = cache.get(fieldMeta);
            fieldList.add(f);
        }
        builder.setFieldList(fieldList);
        lastTableMap = new TableMapLogEvent(rebuild(builder), this, fde);
        return lastTableMap;
    }

    public SequenceLogEvent generateCMTSequenceEvent() throws Exception {
        SequenceEventBuilder builder = new SequenceEventBuilder((int) (System.currentTimeMillis() / 1000),
            2,
            1,
            System.currentTimeMillis());
        return new SequenceLogEvent(rebuild(builder), this, fde);
    }

    public SequenceLogEvent generateSNAPSequenceEvent() throws Exception {
        SequenceEventBuilder builder = new SequenceEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            1,
            System.currentTimeMillis());
        return new SequenceLogEvent(rebuild(builder), this, fde);
    }

    public RowsLogEvent generateRowsLogEvent() throws Exception {
        List<TableMeta.FieldMeta> fieldMetaList = tableMeta.getFields();
        RowEventBuilder rowEventBuilder = new RowEventBuilder(1,
            fieldMetaList.size(),
            BinlogEventType.WRITE_ROWS_EVENT,
            (int) (System.currentTimeMillis() / 1000),
            1);
        List<Field> fieldList = new ArrayList<>();
        RowData rowData = new RowData();
        BitMap bitMap = new BitMap(fieldMetaList.size());
        BitMap nullBitMap = new BitMap(fieldMetaList.size());
        for (int i = 0; i < fieldMetaList.size(); i++) {
            TableMeta.FieldMeta fieldMeta = fieldMetaList.get(i);
            Field f = cache.get(fieldMeta);
            fieldList.add(f);
            bitMap.set(i, true);
            nullBitMap.set(i, f.isNull());
        }
        rowData.setBiFieldList(fieldList);
        rowData.setBiNullBitMap(nullBitMap);
        rowEventBuilder.setColumnsBitMap(bitMap);
        rowEventBuilder.addRowData(rowData);
        WriteRowsLogEvent rowsLogEvent = new WriteRowsLogEvent(rebuild(rowEventBuilder), this, fde);
        rowsLogEvent.setTable(lastTableMap);
        return rowsLogEvent;
    }
}
