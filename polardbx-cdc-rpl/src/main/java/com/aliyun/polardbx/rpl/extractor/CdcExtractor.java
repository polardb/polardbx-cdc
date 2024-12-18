/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.MySqlInfo;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.cdc.DefaultCdcExtractHandler;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

public class CdcExtractor extends BaseExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CdcExtractor.class);
    private String cdcServerIp;
    private Integer cdcPort;
    private EventHandle handle;
    private boolean run;
    private BinlogPosition position;
    private HostInfo hostInfo;
    private BaseFilter baseFilter;
    private Thread parseThread;
    private Throwable ex;
    private MySqlInfo mySqlInfo;

    public CdcExtractor(ExtractorConfig extractorConfig, String cdcServerIp,
                        Integer cdcPort, HostInfo hostInfo, BaseFilter baseFilter, BinlogPosition position) {
        super(extractorConfig);
        this.cdcServerIp = cdcServerIp;
        this.cdcPort = cdcPort;
        this.position = position;
        this.hostInfo = hostInfo;
        this.baseFilter = baseFilter;
    }

    @Override
    public void init() throws Exception {
        super.init();
        initDumperInfo();
        initHostInfo();
        initCharset();
    }

    private void initDumperInfo() throws Exception {
        DumperInfoMapper mapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        RetryTemplate template = RetryTemplate.builder()
            .maxAttempts(120)
            .fixedBackoff(1000)
            .retryOn(RetryableException.class)
            .build();

        DumperInfo info = template.execute((RetryCallback<DumperInfo, Exception>) retryContext -> {
            Optional<DumperInfo> dumperInfo = mapper.selectOne(c -> c
                .where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
            if (!dumperInfo.isPresent()) {
                throw new RetryableException("dumper leader is not ready");
            }
            return dumperInfo.get();
        }, retryContext -> null);
        cdcServerIp = info.getIp();
        cdcPort = info.getPort();
        logger.info("override cdc server ip and port " + cdcServerIp + " : " + cdcPort + " success!");
    }
    private void initHostInfo() {
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
            c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(ServerInfoDynamicSqlSupport.status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        );
        String dstIp = serverInfoList.get(0).getIp();
        Integer dstPort = serverInfoList.get(0).getPort();
        hostInfo.setHost(dstIp);
        hostInfo.setPort(dstPort);
    }

    private void initCharset() throws IOException {
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

    @Override
    public void start() throws Exception {
        super.start();
        this.run = true;
        logger.info("start cdc extractor " + cdcServerIp + " :" + cdcPort);
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(cdcServerIp, cdcPort)
            .usePlaintext()
            .maxInboundMessageSize(0xFFFFFF + 0xFF)
            .build();

        if (position == null) {
            position = FSMMetaManager.findStartPosition(channel);
        }

        Map<String, String> ext = new HashMap<>();
        ext.put("master_binlog_checksum", "CRC32");
        StreamObserverLogFetcher logBuffer = new StreamObserverLogFetcher();
        CdcServiceGrpc.CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        cdcServiceStub.dump(DumpRequest.newBuilder()
            .setFileName(position.getFileName())
            .setExt(JSON.toJSONString(ext))
            .setPosition(4).build(), logBuffer);

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
        LogContext context = new LogContext();
        context.setServerCharactorSet(mySqlInfo.getServerCharactorSet());
        LogPosition logPosition = new LogPosition(position.getFileName(), position.getPosition());
        context.setLogPosition(logPosition);
        parseThread = new Thread(() -> {
            try {
                logger.info("parser thread started!");
                boolean shouldIgnore = true;
                while (run && logBuffer.fetch()) {

                    LogEvent event = decoder.decode(logBuffer, context);

                    if (event == null) {
                        continue;
                    }
                    if (shouldIgnore){
                        if (StringUtils.equals(logPosition.getFileName(), position.getFileName()) &&
                            logPosition.getPosition() < position.getPosition()){
                            continue;
                        }
                        logger.info("parser position ignore end @ "+ logPosition.getFileName()+":"+logPosition.getPosition());
                    }

                    shouldIgnore = false;

                    handle.handle(event, context.getLogPosition());
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
