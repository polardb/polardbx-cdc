/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.aliyun.polardbx.binlog.util.Shell.SYSPROP_CDC_HOME_DIR;

/**
 * created by ziyang.lb
 **/
public class RocksDBOptionUtil {
    private static final Logger rocksDbLogger = LoggerFactory.getLogger("rocksDbLogger");

    /**
     * Some important parameters
     * <p>
     * setMaxWriteBufferNumber()
     * setLevel0StopWritesTrigger()
     * setLevelZeroStopWritesTrigger()
     * setLevel0SlowdownWritesTrigger()
     * setLevelZeroSlowdownWritesTrigger()
     * <p>
     * https://github.com/facebook/rocksdb/wiki/Write-Stalls
     */
    public static Options buildOptions() throws IOException {
        String cdcHomeDir = System.getProperty(SYSPROP_CDC_HOME_DIR);
        File iniFile = null;
        Options options;

        if (StringUtils.isNotBlank(cdcHomeDir)) {
            String rocksDbIniFilePath = cdcHomeDir + File.separator + "conf" + File.separator + "rocksdb.ini";
            iniFile = new File(rocksDbIniFilePath);
        }

        if (iniFile != null && iniFile.exists()) {
            Properties dbOptionsProperties = getSectionProperties(iniFile, "DBOptions");
            DBOptions dbOptions = DBOptions.getDBOptionsFromProps(dbOptionsProperties);

            Properties cfOptionsProperties = getSectionProperties(iniFile, "CFOptions");
            ColumnFamilyOptions cfOptions = ColumnFamilyOptions.getColumnFamilyOptionsFromProps(cfOptionsProperties);
            options = new Options(dbOptions, cfOptions);
        } else {
            options = new Options()
                .setCreateIfMissing(true)
                .setStatsDumpPeriodSec(60);
        }

        options.setMaxBackgroundJobs(Runtime.getRuntime().availableProcessors());
        options.setLogger(buildLogger(options));
        return options;
    }

    public static Properties getSectionProperties(File iniFile, String sectionKey) throws IOException {
        Ini ini = new Ini();
        ini.load(iniFile);
        Profile.Section section = ini.get(sectionKey);
        Properties properties = new Properties();
        section.forEach(properties::setProperty);
        return properties;
    }

    private static org.rocksdb.Logger buildLogger(Options options) {
        org.rocksdb.Logger logger = new org.rocksdb.Logger(options) {
            @Override
            protected void log(InfoLogLevel infoLogLevel, String logMsg) {
                rocksDbLogger.info(logMsg);
            }
        };
        logger.setInfoLogLevel(options.infoLogLevel());
        return logger;
    }
}
