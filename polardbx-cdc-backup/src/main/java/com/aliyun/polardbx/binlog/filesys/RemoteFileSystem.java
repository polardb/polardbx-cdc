/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * remote binlog directory:
 * global binlog: polardbx_cdc/instance_name/
 * binlog-x: polardbx_cdc/instance_name/group/stream/
 * remote binlog name:
 * global binlog:binlog.000001
 * binlog-x:stream#binlog.000001
 * 所有接口方法签名中的fileName都是不带任何前缀路径的纯文件名
 * RemoteFileSystem会调用RemoteBinlogProxy，传给它的文件名
 * 增加了前缀"group/stream/"
 *
 * @author yudong
 */
public class RemoteFileSystem implements IFileSystem {
    private final String group;
    private final String stream;
    private final String clusterId;
    private final BinlogOssRecordService recordService;

    public RemoteFileSystem(String group, String stream) {
        this.group = group;
        this.stream = stream;
        this.clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        this.recordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
    }

    @Override
    public boolean delete(String fileName) {
        RemoteBinlogProxy.getInstance().deleteFile(getFullName(fileName));
        return true;
    }

    @Override
    public boolean exist(String fileName) {
        return getFileList().contains(fileName);
    }

    @Override
    public CdcFile get(String fileName) {
        if (exist(fileName)) {
            return new CdcFile(fileName, this);
        }
        return null;
    }

    @Override
    public long size(String fileName) {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper.select(s -> s.where(
            BinlogOssRecordDynamicSqlSupport.binlogFile, SqlBuilder.isEqualTo(fileName)
        ));
        if (recordList.isEmpty()) {
            return -1;
        }
        return recordList.get(0).getLogSize();
    }

    /**
     * 返回远端存储上上传成功，且没有被删除的文件列表
     */
    @Override
    public List<CdcFile> listFiles() {
        List<CdcFile> result = new ArrayList<>();
        List<BinlogOssRecord> records = recordService.getRecordsOfExistingFiles(group, stream, clusterId);
        int preserveDays = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_FILE_PRESERVE_DAYS);
        // 返回未过期的文件。虽然过期文件仍然可能存在oss上没被清理，但这里认为其已经对外不可见。
        Date expireTime = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(preserveDays));
        for (BinlogOssRecord r : records) {
            if (r.getGmtModified().before(expireTime)) {
                continue;
            }
            CdcFile f = new CdcFile(r.getBinlogFile(), this);
            f.setRecord(r);
            f.setLocation("REMOTE");
            result.add(f);
        }
        return result;
    }

    @Override
    public String getFullName(String pureName) {
        return BinlogFileUtil.buildRemoteFilePartName(pureName, group, stream);
    }

    @Override
    public BinlogFileReadChannel getReadChannel(String fileName) {
        return new BinlogFileReadChannel(RemoteBinlogProxy.getInstance().prepareReadChannel(getFullName(fileName)),
            null);
    }

    private List<String> getFileList() {
        List<BinlogOssRecord> existingRecords = recordService.getRecordsOfExistingFiles(group, stream, clusterId);
        return existingRecords.stream().map(BinlogOssRecord::getBinlogFile).sorted().collect(Collectors.toList());
    }
}
