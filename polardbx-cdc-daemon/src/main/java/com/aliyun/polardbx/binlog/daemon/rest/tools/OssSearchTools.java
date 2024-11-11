/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.tools;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheMode;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;

@Slf4j
public class OssSearchTools {
    private String storageInstanceId;
    private String beginTime;
    private Long serverId;
    private ServerCharactorSet serverCharactorSet;

    public OssSearchTools(String storageInstanceId, String beginTime) {
        this.storageInstanceId = storageInstanceId;
        this.beginTime = beginTime;
        CacheManager.getInstance().setMode(CacheMode.DISK);
        CacheManager.getInstance().setPath("/home/admin/logs/polardbx-binlog/Daemon/cache");
    }

    private void initServerInfo() throws IOException {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<Map<String, Object>> dataList =
            metaTemplate.queryForList("select * from storage_info where inst_kind=0 and is_vip = 1");
        String ip = null, port = null, username = null, pwd = null;
        for (Map<String, Object> map : dataList) {
            Object instId = map.get("storage_master_inst_id");
            if (Objects.equals(instId, storageInstanceId)) {
                ip = String.valueOf(map.get("ip"));
                port = String.valueOf(map.get("port"));
                username = String.valueOf(map.get("user"));
                pwd = PasswdUtil.decryptBase64(String.valueOf(map.get("passwd_enc")));
                break;
            }
        }

        if (StringUtils.isBlank(ip) || StringUtils.isBlank(port) || StringUtils.isBlank(username) || StringUtils
            .isBlank(pwd)) {
            throw new PolardbxException("storageInstId expect size 1 , query failed ");
        }

        AuthenticationInfo auth =
            new AuthenticationInfo(new InetSocketAddress(ip, Integer.valueOf(port)), username, pwd);
        MysqlConnection connection = new MysqlConnection(auth);
        connection.connect();
        try {
            serverCharactorSet = connection.getDefaultDatabaseCharset();
            serverId = connection.query("SELECT @@server_id", rs -> {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return null;
            });
        } finally {
            connection.disconnect();
        }

        if (serverId == null) {
            throw new PolardbxException("can not find serverId");
        }
    }

    private List<BinlogFile> buildFileList() throws Exception {

        String uid = DynamicApplicationConfig.getString(RDS_UID);
        String bid = DynamicApplicationConfig.getString(RDS_BID);
        long end = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long begin = sdf.parse(beginTime).getTime();
        DescribeBinlogFilesResult result = RdsApi
            .describeBinlogFiles(storageInstanceId, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                RdsApi.formatUTCTZ(new Date(end)),
                1000,
                1);

        return BinlogProcessor.process(result.getItems(), new HashSet<>(), null, begin, serverId);
    }

    private URLLogFetcher providerFetcher(BinlogFile binlogFile) throws IOException {
        URLLogFetcher fetcher = new URLLogFetcher();
        fetcher.open(binlogFile.getIntranetDownloadLink(), 0, binlogFile.getFileSize());
        log.info("provider fetcher url fetcher url ： " + binlogFile.getDownloadLink());
        return fetcher;
    }

    public void doSearch(Set<Integer> interestSet, EventProcessor processor) throws Exception {
        initServerInfo();
        List<BinlogFile> binlogFileList = buildFileList();
        LogDecoder decoder = new LogDecoder();
        for (Integer id : interestSet) {
            decoder.handle(id);
        }
        LogContext lc = new LogContext();
        lc.setLogPosition(new LogPosition(binlogFileList.get(0).getLogname(), 0));
        lc.setServerCharactorSet(serverCharactorSet);
        for (BinlogFile binlogFile : binlogFileList) {
            URLLogFetcher fetcher = providerFetcher(binlogFile);

            while (fetcher.fetch()) {
                LogEvent event = decoder.decode(fetcher, lc);
                if (event == null) {
                    continue;
                }
                processor.process(binlogFile, event);
            }
        }
    }

    public void doSearch(String searchFileName, Set<Integer> interestSet, EventProcessor processor) throws Exception {
        initServerInfo();
        List<BinlogFile> binlogFileList = buildFileList();
        BinlogFile searchFile = null;
        for (BinlogFile bf : binlogFileList) {
            if (StringUtils.equals(bf.getLogname(), searchFileName)) {
                searchFile = bf;
                break;
            }
        }
        LogDecoder decoder = new LogDecoder();
        for (Integer id : interestSet) {
            decoder.handle(id);
        }
        LogContext lc = new LogContext();
        lc.setLogPosition(new LogPosition(binlogFileList.get(0).getLogname(), 0));
        lc.setServerCharactorSet(serverCharactorSet);
        URLLogFetcher fetcher = providerFetcher(searchFile);
        while (fetcher.fetch()) {
            LogEvent event = decoder.decode(fetcher, lc);
            if (event == null || event.getHeader().getType() == LogEvent.QUERY_EVENT) {
                continue;
            }
            processor.process(searchFile, event);
        }
    }
}
