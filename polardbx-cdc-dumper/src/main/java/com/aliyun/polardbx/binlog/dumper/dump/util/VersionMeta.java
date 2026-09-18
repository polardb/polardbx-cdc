/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionMeta {
    private static String VERSION_META = "VERSION_META";

    private Long version;
    private Long subVersion;
    private Set<String> streamSet;

    public void update() throws IOException {
        String rootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, version);
        String path = rootPath + File.separator + VERSION_META;
        FileUtils.writeStringToFile(new File(path), JSONObject.toJSONString(this), "UTF-8");
    }

    @SneakyThrows
    public static VersionMeta query(long version) {
        String rootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, version);
        String path = rootPath + File.separator + VERSION_META;

        File file = new File(path);
        if (file.exists() && file.length() > 0) {
            String content = FileUtils.readFileToString(new File(path), "UTF-8");
            return JSONObject.parseObject(content, VersionMeta.class);
        }
        return null;
    }
}
