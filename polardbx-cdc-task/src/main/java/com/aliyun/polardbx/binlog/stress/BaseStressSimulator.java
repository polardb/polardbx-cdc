/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
