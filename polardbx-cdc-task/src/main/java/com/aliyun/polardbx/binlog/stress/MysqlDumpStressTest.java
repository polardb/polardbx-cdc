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
package com.aliyun.polardbx.binlog.stress;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MysqlDumpStressTest {
    private static long byteSize = 0;
    private static long lastPrintTime = 0;
    private static Thread thread;

    // sh stress.sh MysqlDumpStressTest "host=xxx port=xxx username=xxx password=xxx filename=xxx blocking=xxx"
    public static void main(String[] args) {
        try {
            Map<String, String> parameter = handleArgs(args[0]);
            String host = parameter.get("host");
            String port = parameter.get("port");
            String username = parameter.get("username");
            String password = parameter.get("password");
            String filename = parameter.get("filename");
            String blocking = parameter.get("blocking");

            dump(host, port, username, password, filename, StringUtils.isNotBlank(blocking));
        } catch (Throwable t) {
            log.error("dump stress test error!!", t);
            System.exit(1);
        }
    }

    private static void dump(String host, String port, String username, String password, String filename,
                             boolean isBlocking) {
        thread = new Thread(() -> {
            try {
                InetSocketAddress socketAddress = new InetSocketAddress(host, Integer.parseInt(port));
                AuthenticationInfo authenticationInfo = new AuthenticationInfo();
                authenticationInfo.setAddress(socketAddress);
                authenticationInfo.setUsername(username);
                authenticationInfo.setPassword(password);
                authenticationInfo.setCharset("utf-8");
                MysqlConnection mysqlConnection = new MysqlConnection(authenticationInfo);
                MysqlConnection metaConnection = new MysqlConnection(authenticationInfo);
                metaConnection.connect();
                ServerCharactorSet serverCharactorSet = metaConnection.getDefaultDatabaseCharset();
                mysqlConnection.setServerCharactorset(serverCharactorSet);

                mysqlConnection.connect();
                mysqlConnection.dump(filename, 4L, null, new SinkFunction() {
                    @Override
                    public boolean sink(LogEvent event, LogPosition logPosition)
                        throws CanalParseException {
                        if (isBlocking) {
                            try {
                                Thread.sleep(3600 * 1000);
                            } catch (InterruptedException e) {
                            }
                        }

                        byteSize += event.toBytes().length;
                        long interval = System.currentTimeMillis() - lastPrintTime;
                        if (interval >= 5000) {
                            long currentTimeSecond = System.currentTimeMillis() / 1000;
                            long delay = currentTimeSecond - event.getWhen();
                            double bps = (Double.valueOf(byteSize) / interval) * 1000;

                            log.info("bps from mysql is : " + new DecimalFormat("#.00").format(bps));
                            log.info("current time : " + currentTimeSecond + ", event time " + event.getWhen());
                            log.info("delay time is : " + delay + "(s)");
                            log.info("=================================================");
                            lastPrintTime = System.currentTimeMillis();
                            byteSize = 0;
                        }
                        if (event instanceof RotateLogEvent) {
                            RotateLogEvent rotateLogEvent = (RotateLogEvent) event;
                            log.info("binlog file name rotate to " + rotateLogEvent.getFilename());
                        }
                        return true;
                    }
                });
                log.info("mysql dump stopped");
            } catch (Throwable t) {
                log.error("something goes wrong !!", t);
            }
        });
        thread.setDaemon(false);
        thread.start();
    }

    public static Map<String, String> handleArgs(String arg) {
        Map<String, String> propMap = new HashMap<String, String>();
        String[] argpiece = arg.split(" ");
        for (String argstr : argpiece) {
            String[] kv = argstr.split("=");
            if (kv.length == 2) {
                propMap.put(kv[0], kv[1]);
            } else if (kv.length == 1) {
                propMap.put(kv[0], StringUtils.EMPTY);
            } else {
                throw new RuntimeException("parameter format need to like: key1=value1 key2=value2 ...");
            }
        }
        return propMap;
    }
}
