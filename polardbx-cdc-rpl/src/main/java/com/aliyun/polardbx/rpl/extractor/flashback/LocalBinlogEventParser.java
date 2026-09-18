/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.rpl.extractor.MysqlEventParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TimerTask;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * @author ziyang.lb
 */
@Slf4j
public class LocalBinlogEventParser extends MysqlEventParser {

    private final ILocalBinlogEventListener eventListener;

    private final boolean needWait;

    private List<String> binlogList;

    private MysqlConnection metaConnection;

    private final String localDirectory;

    private final boolean needCareServerId;

    public LocalBinlogEventParser(int bufferSize, boolean needWait, ILocalBinlogEventListener eventListener,
                                  boolean needCareServerId, EventRepository eventRepository) {
        super(bufferSize, eventRepository);
        this.needWait = needWait;
        this.eventListener = eventListener;
        this.localDirectory = DynamicApplicationConfig.getString(ConfigKeys.FLASHBACK_BINLOG_DOWNLOAD_DIR)
            + System.getProperty(TASK_NAME) + File.separator;
        this.needCareServerId = needCareServerId;
    }

    @Override
    protected ErosaConnection buildErosaConnection() {
        log.info("start build local connection");
        return new LocalBinLogConnection(localDirectory, binlogList, needWait, eventListener, currentServerId);
    }

    /**
     * 本地 binlog 场景仅读取本地文件，不涉及 MySQL 连接，因此不需要 MySQL 心跳检测。
     * 此处返回 null 以跳过心跳任务的创建，避免父类 MysqlEventParser 因连接类型为
     * LocalBinLogConnection（非 MysqlConnection）而抛出 "Unsupported connection type" 异常。
     */
    @Override
    protected TimerTask buildHeartBeatTimeTask(ErosaConnection connection) {
        return null;
    }

    @Override
    public void preDump(ErosaConnection connection) {
        metaConnection = new MysqlConnection(runningInfo);
        try {
            metaConnection.connect();
        } catch (IOException e) {
            throw new CanalParseException(e);
        }

        if (serverCharactorSet == null) {
            serverCharactorSet = metaConnection.getDefaultDatabaseCharset();
            if (serverCharactorSet == null || serverCharactorSet.getCharacterSetClient() == null
                || serverCharactorSet.getCharacterSetConnection() == null
                || serverCharactorSet.getCharacterSetDatabase() == null
                || serverCharactorSet.getCharacterSetServer() == null) {
                throw new CanalParseException("not found default database charset");
            }
        }

        ((LocalBinLogConnection) connection).setServerCharactorSet(serverCharactorSet);
        lowerCaseTableNames = metaConnection.getLowerCaseTableNames();
    }

    @Override
    protected void afterDump(ErosaConnection connection) {
        if (metaConnection != null) {
            try {
                metaConnection.disconnect();
            } catch (IOException e) {
                log.error("ERROR # disconnect meta connection for address:" + metaConnection.getAddress(), e);
            }
        }
    }

//    @Override
//    public void stop() {
//        if (metaConnection != null) {
//            try {
//                metaConnection.disconnect();
//            } catch (IOException e) {
//                log.error("ERROR # disconnect meta connection for address:{}", metaConnection.toString(), e);
//            }
//        }
//        super.stop();
//    }

    @Override
    public BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) {
        if (!needCareServerId && position != null) {
            return position;
        }
        log.warn("try to find local binlog file");
        File firstBinlogFile = ((LocalBinLogConnection) connection).firstBinlogFile();
        // 前面按时间找到了对应的binlog，这里直接从该binlog的开头开始解析
        if (firstBinlogFile != null) {
            log.warn("try to find local binlog file: {}", firstBinlogFile.getName());
            binlogParser.setBinlogFileName(firstBinlogFile.getName());
            return new BinlogPosition(firstBinlogFile.getName(), 0L, -1, -1);
        }
        log.error("can not find local binlog file");
        return null;
    }

    public void setBinlogList(List<String> binlogList) {
        this.binlogList = binlogList;
    }
}
