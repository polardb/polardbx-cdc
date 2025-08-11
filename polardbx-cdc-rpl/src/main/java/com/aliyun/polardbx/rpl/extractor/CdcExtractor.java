/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.MySqlInfo;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.rpc.EndPoint;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.cdc.DefaultCdcExtractHandler;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class CdcExtractor extends BaseExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CdcExtractor.class);
    @Getter
    private String cdcServerIp;
    @Getter
    private Integer cdcPort;
    private EventHandle handle;
    private boolean run;
    private BinlogPosition position;
    @Getter
    private HostInfo hostInfo;
    private BaseFilter baseFilter;
    private Thread parseThread;
    private Throwable ex;
    private MySqlInfo mySqlInfo;
    @Setter
    private ServerInfoMapper serverInfoMapper;
    @Setter
    private DumperInfoMapper dumperInfoMapper;
    @Setter
    private XStreamMapper xStreamMapper;
    @Setter
    private String streamName;
    @Setter
    private ManagedChannel channel;
    @Setter
    private CdcServiceGrpc.CdcServiceStub cdcServiceStub;

    public CdcExtractor(ExtractorConfig extractorConfig, HostInfo hostInfo, BaseFilter baseFilter,
                        BinlogPosition position) {
        super(extractorConfig);
        this.position = position;
        this.hostInfo = hostInfo;
        this.baseFilter = baseFilter;
        this.serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        this.dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        this.xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
    }

    public void setServerInfoMapper(ServerInfoMapper serverInfoMapper) {
        this.serverInfoMapper = serverInfoMapper;
    }

    @Override
    public void init() throws Exception {
        super.init();
        initDumperInfo();
        initHostInfo();
        initCharset();
    }

    public void initDumperInfo() {
        DataImportMeta.PhysicalMeta physicalMeta = TaskContext.getInstance().getPhysicalMeta();
        streamName = physicalMeta.getStreamName();
        if (StringUtils.isNotBlank(streamName)) {
            Optional<XStream> xStream = xStreamMapper.selectOne(c -> c
                .where(XStreamDynamicSqlSupport.streamName, IsEqualTo.of(() -> streamName)));
            if (!xStream.isPresent()) {
                throw new PolardbxException("xstream endpoint is not ready");
            }
            EndPoint endPoint = JSON.parseObject(xStream.get().getEndpoint(), EndPoint.class);
            cdcServerIp = endPoint.getHost();
            cdcPort = endPoint.getPort();
        } else {
            Optional<DumperInfo> dumperInfo = dumperInfoMapper.selectOne(c -> c
                .where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
            if (!dumperInfo.isPresent()) {
                throw new PolardbxException("dumper leader is not ready");
            }
            cdcServerIp = dumperInfo.get().getIp();
            cdcPort = dumperInfo.get().getPort();
        }
        logger.info("cdc extractor " + cdcServerIp + " :" + cdcPort);
        channel = ManagedChannelBuilder
            .forAddress(cdcServerIp, cdcPort)
            .usePlaintext()
            .maxInboundMessageSize(0xFFFFFF + 0xFF)
            .build();
        cdcServiceStub = CdcServiceGrpc.newStub(channel);
        logger.info("override cdc server ip and port " + cdcServerIp + " : " + cdcPort + " success!");
    }

    void initHostInfo() {
        List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
            c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(ServerInfoDynamicSqlSupport.status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        );
        String dstIp = serverInfoList.get(0).getIp();
        Integer dstPort = serverInfoList.get(0).getPort();
        hostInfo.setHost(dstIp);
        hostInfo.setPort(dstPort);
        hostInfo.setUserName(DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME));
        hostInfo.setPassword(DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD));
    }

    public void initCharset() throws IOException {
        mySqlInfo = new MySqlInfo();
        String ip = hostInfo.getHost();
        int port = hostInfo.getPort();
        String user = hostInfo.getUserName();
        String password = hostInfo.getPassword();
        AuthenticationInfo authInfo = new AuthenticationInfo(new InetSocketAddress(ip, port), user, password);
        MysqlConnection connection = new MysqlConnection(authInfo);
        connection.connect();
        mySqlInfo.init(connection);
    }

    public LogContext providerLogContext(){
        LogContext context = new LogContext();
        context.setFormatDescription(new FormatDescriptionLogEvent(4, mySqlInfo.getBinlogChecksum()));
        context.setServerCharactorSet(mySqlInfo.getServerCharactorSet());
        context.setLogPosition(new LogPosition(position.getFileName(), position.getPosition()));
        return context;
    }

    @Override
    public void start() throws Exception {
        super.start();
        this.run = true;
        logger.info("start cdc extractor " + cdcServerIp + " :" + cdcPort);

        StreamObserverLogFetcher logBuffer = provideLogBuffer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logBuffer.close();
                channel.shutdownNow();
            } catch (IOException ioException) {

            }
        }));

        ImportLogEventConvert convert = new ImportLogEventConvert(hostInfo, baseFilter, position, HostType.POLARX2);
        convert.init();
        handle = new DefaultCdcExtractHandler(convert, pipeline, this);

        handle.onStart();
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        decoder.setNeedFixRotate(false);
        LogContext context = providerLogContext();
        parseThread = new Thread(() -> {
            try {
                logger.info("parser thread started!");
                boolean shouldIgnore = true;
                while (run && logBuffer.fetch()) {

                    LogEvent event = decoder.decode(logBuffer, context);

                    if (event == null) {
                        continue;
                    }

                    LogPosition logPosition = context.getLogPosition();

                    if (shouldIgnore) {
                        if (StringUtils.equals(logPosition.getFileName(), position.getFileName()) &&
                            logPosition.getPosition() < position.getPosition()) {
                            continue;
                        }
                        logger.info("parser position ignore end @ " + logPosition.getFileName() + ":"
                            + logPosition.getPosition());
                    }

                    shouldIgnore = false;

                    handle.handle(event, logPosition);
                    if (handle.interrupt()) {
                        logger.warn(" handler interrupt");
                        break;
                    }
                    StatisticalProxy.getInstance().heartbeat();
                }
                logger.error("event process or end run : " + run);
            } catch (Throwable e) {
                ex = e;
            } finally {
                handle.onEnd();
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                    TaskContext.getInstance().getTaskId(), "back flow process error");
                StatisticalProxy.getInstance().recordLastError(ex.toString());
                TaskContext.getInstance().getPipeline().stop();
            }

        }, "parser-thread");

        parseThread.start();
        logger.info("start cdc extractor start success");
    }

    public boolean isCrc32(){
        return mySqlInfo.getBinlogChecksum() == LogEvent.BINLOG_CHECKSUM_ALG_CRC32;
    }

    public StreamObserverLogFetcher provideLogBuffer() throws InterruptedException, IOException {
        if (position == null) {
            if (StringUtils.isNotBlank(streamName)) {
                position = FSMMetaManager.findStreamStartPosition(streamName);
            } else {
                position = FSMMetaManager.findStartPosition(channel);
            }
        }

        Map<String, String> ext = new HashMap<>();
        if (isCrc32()){
            ext.put("master_binlog_checksum", "CRC32");
        }
        StreamObserverLogFetcher logBuffer = providerLogFetcher();
        if (StringUtils.isNotBlank(streamName)) {
            cdcServiceStub.dump(DumpRequest.newBuilder()
                .setStreamName(streamName)
                .setFileName(position.getFileName())
                .setExt(JSON.toJSONString(ext))
                .setPosition(4).build(), logBuffer);
        } else {
            cdcServiceStub.dump(DumpRequest.newBuilder()
                .setFileName(position.getFileName())
                .setExt(JSON.toJSONString(ext))
                .setPosition(4).build(), logBuffer);
        }
        return logBuffer;
    }

    public StreamObserverLogFetcher providerLogFetcher() throws IOException {
        return new StreamObserverLogFetcher();
    }

    @Override
    public void stop() {
        super.stop();
        this.run = false;
    }

    @Override
    public boolean isDone() {
        if (ex != null) {
            throw new PolardbxException(ex);
        }
        return false;
    }
}
