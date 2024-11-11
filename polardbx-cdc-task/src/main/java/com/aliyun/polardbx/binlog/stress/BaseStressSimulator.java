/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.stress;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by ziyang.lb
 **/
public class BaseStressSimulator {

    private static final Logger logger = LoggerFactory.getLogger(BaseStressSimulator.class);

    public static Map<String, String> handleArgs(String arg) {
        Map<String, String> propMap = new HashMap<String, String>();
        String[] argpiece = arg.split(" ");
        for (String argstr : argpiece) {
            String[] kv = argstr.split("=");
            if (kv.length == 2) {
                propMap.put(kv[0], kv[1]);
                System.setProperty(kv[0], kv[1]);
                logger.info("Args key: " + kv[0] + "; Args Value: " + kv[1]);
            } else if (kv.length == 1) {
                propMap.put(kv[0], StringUtils.EMPTY);
                System.setProperty(kv[0], StringUtils.EMPTY);
                logger.info("Args key: " + kv[0] + "; Args Value: " + kv[1]);
            } else {
                throw new RuntimeException("parameter format need to like: key1=value1 key2=value2 ...");
            }
        }
        return propMap;
    }

    public static String getValue(String key, String defaultValue) {
        String s = System.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            logger.info("Property Value for [" + key + "] is " + s);
            return s;
        } else {
            logger.info("Property Value for [" + key + "] is " + s);
            return defaultValue;
        }
    }

    public static int getRandomEventSize() {
        int min = 768;
        int max = 1024;
        Random random = new Random();
        return random.nextInt(max) % (max - min + 1) + min;
    }
}
