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
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
 */
public class RemoteFileSystem implements ICdcFileSystem {
    private final String group;
    private final String stream;

    public RemoteFileSystem(String group, String stream) {
        this.group = group;
        this.stream = stream;
    }

    @Override
    public CdcFile create(String fileName) {
        return new CdcFile(fileName, this);
    }

    @Override
    public boolean delete(String fileName) {
        RemoteBinlogProxy.getInstance()
            .deleteFile(getName(fileName));
        return true;
    }

    @Override
    public boolean exist(String fileName) {
        Set<String> fileNames = getExistingRemoteFilesFromTable();
        return fileNames.contains(fileName);
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
     * 从binlog_oss_record表中查记录，
     * 返回上传成功（uploadStatus == 2) 并且还没有被清理（purgeStatus == 0）的记录
     */
    @Override
    public List<CdcFile> listFiles() {
        List<CdcFile> res = new ArrayList<>();
        Set<String> remoteFileNames = getExistingRemoteFilesFromTable();
        for (String fileName : remoteFileNames) {
            if (BinlogFileUtil.isBinlogFile(fileName)) {
                res.add(new CdcFile(fileName, this));
            }
        }
        return res;
    }

    /**
     * 使用TreeSet保证binlog文件名的有序性
     */
    private Set<String> getExistingRemoteFilesFromTable() {
        Set<String> res = new TreeSet<>();
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper.select(s -> s.where(
                BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(group)).
            and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(stream)).
            and(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                SqlBuilder.isEqualTo(BinlogUploadStatusEnum.SUCCESS.getValue())).
            and(BinlogOssRecordDynamicSqlSupport.purgeStatus,
                SqlBuilder.isEqualTo(BinlogPurgeStatusEnum.UN_COMPLETE.getValue()))
        );
        recordList.forEach(r -> res.add(r.getBinlogFile()));
        return res;
    }

    @Override
    public String getName(String pureName) {
        return BinlogFileUtil.buildRemoteFilePartName(pureName, group, stream);
    }

    @Override
    public BinlogFileReadChannel getReadChannel(String fileName) {
        return new BinlogFileReadChannel(
            RemoteBinlogProxy.getInstance()
                .prepareReadChannel(getName(fileName)),
            null);
    }
}
