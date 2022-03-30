package com.aliyun.polardbx.binlog.canal;

import java.io.IOException;

/**
 * Binlog文件大小查询器
 * Created by ziyang.lb
 */
public interface IBinlogFileSizeFetcher {
    /**
     * 获取指定binlog文件的文件大小
     */
    long fetch(String fileName) throws IOException;
}
