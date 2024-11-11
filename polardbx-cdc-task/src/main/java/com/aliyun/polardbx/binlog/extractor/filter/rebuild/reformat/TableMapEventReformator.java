/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.EventReformater;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMapEventRebuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.google.protobuf.UnsafeByteOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableMapEventReformator implements EventReformater<TableMapLogEvent> {

    private static final Logger log = LoggerFactory.getLogger("rebuildEventLogger");
    private final PolarDbXTableMetaManager tableMetaManager;

    public TableMapEventReformator(PolarDbXTableMetaManager tableMetaManager) {
        this.tableMetaManager = tableMetaManager;
    }

    @Override
    public Set<Integer> interest() {
        Set<Integer> idSet = new HashSet<>();
        idSet.add(LogEvent.TABLE_MAP_EVENT);
        return idSet;
    }

    @Override
    public boolean accept(TableMapLogEvent event) {
        if (SystemDB.isSys(event.getDbName())) {
            return false;
        }
        return true;
    }

    @Override
    public void register(Map<Integer, EventReformater> map) {
        map.put(LogEvent.TABLE_MAP_EVENT, this);
    }

    @Override
    public boolean reformat(TableMapLogEvent tle, TxnItemRef txnItemRef, ReformatContext context, EventData eventData)
        throws Exception {

        final long serviceId = context.getServerId();
        LogicTableMeta tableMeta = tableMetaManager.compare(tle.getDbName(), tle.getTableName(), tle.getColumnCnt());
        if (log.isDebugEnabled()) {
            log.debug("detected un compatible table meta for table map event, will reformat event "
                + tableMeta.getPhySchema() + tableMeta.getPhyTable());
        }
        String characterServer = context.getCharsetServer();
        TableMapEventBuilder tme =
            TableMapEventRebuilder.convert(tle, serviceId, CharsetConversion.getJavaCharset(characterServer));
        if (!tableMeta.isCompatible()) {
            try {
                rebuildTableMapBuilder(tme, tableMeta, context.getDefaultCharset());
            } catch (Exception e) {
                TableMapLogEvent.ColumnInfo[] columnInfo = tle.getColumnInfo();
                StringBuilder errorInfo = new StringBuilder();
                for (LogicTableMeta.FieldMetaExt fieldMetaExt : tableMeta.getLogicFields()) {
                    if (fieldMetaExt.getPhyIndex() >= columnInfo.length) {
                        errorInfo.append("not found phy columnIndex ").append(fieldMetaExt.getPhyIndex())
                            .append(" with column name : ").append(fieldMetaExt.getColumnName());
                    }
                    if (fieldMetaExt.getLogicIndex() >= tableMeta.getLogicFields().size()) {
                        errorInfo.append("not found logic columnIndex ").append(fieldMetaExt.getLogicIndex())
                            .append(" with column name : ").append(fieldMetaExt.getColumnName());
                    }
                }
                log.error("rebuild table map error " + tme.getSchema() + "." + tme.getTableName()
                    + " error : " + errorInfo, e);
                throw e;
            }
        }

        tme.setSchema(tableMeta.getLogicSchema());
        tme.setTableName(tableMeta.getLogicTable());
        eventData = eventData.toBuilder()
            .setSchemaName(tableMeta.getLogicSchema())
            .setTableName(tableMeta.getLogicTable())
            .setPayload(UnsafeByteOperations.unsafeWrap(ReformatContext.toByte(tme))).build();
        txnItemRef.setEventData(eventData);
        if (log.isDebugEnabled()) {
            log.debug("table map event : " + JSONObject.toJSONString(tle.toBytes()));
        }

        return true;
    }

    private void rebuildTableMapBuilder(TableMapEventBuilder tme, LogicTableMeta tableMeta, String defaultCharset) {
        byte[] typeDef = tme.getColumnDefType();
        byte[][] metaDef = tme.getColumnMetaData();
        BitMap nullBitmap = tme.getNullBitmap();
        List<LogicTableMeta.FieldMetaExt> fieldMetas = tableMeta.getLogicFields();
        int newColSize = fieldMetas.size();
        byte[] newTypeDef = new byte[newColSize];
        byte[][] newMetaDef = new byte[newColSize][];
        BitMap newNullBitMap = new BitMap(newColSize);

        for (LogicTableMeta.FieldMetaExt fieldMetaExt : fieldMetas) {
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();

            if (fieldMetaExt.isTypeMatch() && phyIndex >= 0) {
                newTypeDef[logicIndex] = typeDef[phyIndex];
                newMetaDef[logicIndex] = metaDef[phyIndex];
                newNullBitMap.set(logicIndex, nullBitmap.get(phyIndex));
            } else {
                String mysqlCharset = fieldMetaExt.getCharset();
                Field field = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    null,
                    mysqlCharset,
                    fieldMetaExt.isNullable(),
                    fieldMetaExt.isUnsigned());
                if (field == null) {
                    String errorMsg = String.format("not support for add new Field: %s.%s %s",
                        tme.getSchema(),
                        fieldMetaExt.getColumnName(),
                        fieldMetaExt.getColumnType());
                    log.error(errorMsg);
                    throw new PolardbxException(errorMsg);
                }
                newTypeDef[logicIndex] = (byte) field.getMysqlType().getType();
                newMetaDef[logicIndex] = field.doGetTableMeta();
                newNullBitMap.set(logicIndex, field.isNullable());
            }
        }
        tme.setColumnDefType(newTypeDef);
        tme.setColumnMetaData(newMetaDef);
        tme.setNullBitmap(newNullBitMap);
    }

}
