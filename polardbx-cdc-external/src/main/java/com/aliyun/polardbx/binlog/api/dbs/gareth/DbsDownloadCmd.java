/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.util.Shell;
import com.amazonaws.util.CollectionUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Slf4j
public abstract class DbsDownloadCmd {

    protected String garethConfig;

    public DbsDownloadCmd(String garethConfig) {
        this.garethConfig = garethConfig;
    }

    protected abstract JSONObject buildSrcInfo();

    protected JSONObject buildJobConfig(String srcPath, String localFile){
        JSONObject jobConfig = new JSONObject();
        jobConfig.put("src_path", srcPath);
        jobConfig.put("dest_path", localFile);
        jobConfig.put("decompress", true);
        return jobConfig;
    }

    protected JSONObject buildDestInfo(){
        JSONObject destConfig = new JSONObject();
        destConfig.put("type", "file");
        return destConfig;
    }

    protected String generateConfigJson(String srcPath, String localFile){
        JSONObject config = new JSONObject();
        config.put("job_type", "transfer");
        config.put("src", buildSrcInfo());
        config.put("job", buildJobConfig(srcPath, localFile));
        config.put("dest", buildDestInfo());
        return config.toJSONString().replaceAll("\"", "\\\\\"");
    }

    public void download(String localFile, String removeFilePath, String logPath)
        throws IOException {
        String config_str = generateConfigJson(removeFilePath,localFile);
        String[] gareth_cmd = buildGarethCmd(config_str , logPath+File.separator+ "gareth.log");
        log.info("execute cmd : {}", CollectionUtils.join(Arrays.asList(gareth_cmd), " "));
        Shell.execCommand(gareth_cmd);
    }


    protected String[] buildGarethCmd(String config, String logFile){
        return new String[]{"sudo","gareth","--engine","default","--config", "\""+config+"\"" ,"--logfile",logFile,"--error2stdout"};
    }

}
