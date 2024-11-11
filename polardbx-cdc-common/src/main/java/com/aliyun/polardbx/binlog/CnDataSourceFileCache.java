/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * CDC在启动之初需要获得meta db的连接，正常情况下管控会把meta db的信息写入env文件
 * 但是在某些异常情况下，比如 meta db发生了ha切换，但是管控没有刷新env文件，CDC就会无法启动
 * 为了减轻CDC对管控的依赖，引入cn datasource file cache，对应CDC容器中的一个文件
 * 该文件保存了最近的cn url， username 和 password等信息，使用该信息可以连接CN，获得meta 的url
 * 依赖路径：CnDataSource -> metaDbDataSource -> env文件 & cn datasource file cache
 *
 * @author yudong
 * @since 2023/8/2 13:59
 **/
@Slf4j
public class CnDataSourceFileCache {

    private volatile static CnDataSourceFileCache instance;
    private String filepath;

    private CnDataSourceFileCache() {
    }

    public static CnDataSourceFileCache getInstance() {
        if (instance == null) {
            synchronized (CnDataSourceFileCache.class) {
                if (instance == null) {
                    instance = new CnDataSourceFileCache();
                }
            }
        }
        return instance;
    }

    public void init(String filePath) {
        log.info("init cn data source file:{}", filePath);
        this.filepath = filePath;
        try {
            File f = new File(filepath);
            FileUtils.forceMkdirParent(f);
        } catch (IOException e) {
            log.error("create data source file error, filepath:{}", filepath);
            throw new PolardbxException(e);
        }
    }

    public void write(CnDataSourceStruct struct) {
        try {
            Path path = Paths.get(filepath);
            Files.write(path, JSON.toJSONString(struct).getBytes());
        } catch (IOException e) {
            log.error("persist latest server address error", e);
        }
    }

    public CnDataSourceStruct read() {
        CnDataSourceStruct result = null;
        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            String json = new String(bytes);
            result = JSON.parseObject(json, CnDataSourceStruct.class);
        } catch (Exception e) {
            log.error("get latest server address from file error", e);
        }

        return result;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class CnDataSourceStruct {
        private Set<String> urls;
        private String username;
        private String password;
        private String polarxInstId;
    }
}
