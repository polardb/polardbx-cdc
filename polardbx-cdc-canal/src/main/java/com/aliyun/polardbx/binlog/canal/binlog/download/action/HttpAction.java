/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.lang3.StringUtils;

public class HttpAction implements IDownloadAction {
    @Override
    public void exec(String storageInstanceId, String localPath, BinlogFile binlogFile) throws Exception {
        String link = binlogFile.getIntranetDownloadLink();
        if (StringUtils.isBlank(link)){
            link = binlogFile.getDownloadLink();
        }
        HttpHelper.download(link, localPath);
    }
}
