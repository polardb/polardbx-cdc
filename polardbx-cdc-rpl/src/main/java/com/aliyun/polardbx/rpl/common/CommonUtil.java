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
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author shicai.xsc 2021/1/14 17:50
 * @since 5.0.0.0
 */
@Slf4j
public class CommonUtil {

    private static final String SHOW_COMPUTE_NODE =
        "select ip, port, inst_id from metadb.server_info where inst_type=0 and status=0";
    private static final String SHOW_BINARY_STREAMS =
        "show binary streams";
    private static final String SHOW_MASTER_STATUS =
        "show master status";

    public static String createInitialBinlogPosition() {
        String timeStr = new SimpleDateFormat(RplConstants.DEFAULT_DATE_FORMAT).format(System.currentTimeMillis());
        return CommonUtil.getRollBackBinlogPosition(timeStr);
    }

    public static boolean isMeaninglessBinlogFileName(BinlogPosition binlogPosition) {
        return (StringUtils.isBlank(binlogPosition.getFileName()) || binlogPosition.getFileName().equals("0"));
    }

    public static int comparePosition(String positionA, String positionB) {
        List<String> detailsA = parsePosition(positionA);
        List<String> detailsB = parsePosition(positionB);

        int res = StringUtils.compare(detailsA.get(0), detailsB.get(0));
        if (res != 0) {
            return res;
        }
        return StringUtils.compare(detailsA.get(1), detailsB.get(1));
    }

    public static List<String> parsePosition(String position) {
        // mysql_bin.000012:0000002388#1.1611938857
        List<String> details = new ArrayList<>();
        String[] tokens = position.split("#");

        String logFile = StringUtils.split(tokens[0], ":")[0];
        String logPos = StringUtils.split(tokens[0], ":")[1];
        details.add(logFile);
        details.add(trimLeftAll(logPos, '0'));

        // if (tokens.length > 1 && tokens[1].contains(".")) {
        // String rawTimeStamp = StringUtils.split(position, ".")[1];
        // Date date = new Date(rawTimeStamp + "000");
        // String timeStamp = new
        // SimpleDateFormat(RplConstants.DEFAULT_DATE_FORMAT).format(date);
        // details.add(timeStamp);
        // }

        return details;
    }

    public static String removeBracket(String s) {
        s = s.trim();
        s = trimLeftOne(s, '(');
        s = trimRightOne(s, ')');
        return s;
    }

    public static String trimLeftOne(String s, char c) {
        if (s.length() > 0 && s.charAt(0) == c) {
            return s.substring(1);
        }
        return s;
    }

    public static String trimRightOne(String s, char c) {
        if (s.length() > 0 && s.charAt(s.length() - 1) == c) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String trimLeftAll(String s, char c) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == c) {
            i++;
        }
        return s.substring(i);
    }

    public static String getCurrentTime() {
        Date date = new Date();
        return new SimpleDateFormat(RplConstants.DEFAULT_DATE_FORMAT).format(date);
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

    /**
     * 传入2015-04-16 08:00:00格式的时间字符串，返回这个时间对应的binlog位点
     */
    private static String getRollBackBinlogPosition(String timeStr) {
        Date date = CalendarUtil.toDate(timeStr, CalendarUtil.TIME_PATTERN);
        if (date == null) {
            return null;
        }
        long positionTime = date.getTime() / 1000;
        return "0:0#" + RplConstants.ROLLBACK_STRING + "." + positionTime;
    }

    /**
     * 任务名称构建
     */
    public static String buildRplTaskName(RplTask rplTask) {
        StringBuilder sb = new StringBuilder();
        sb.append("F").append(rplTask.getStateMachineId()).append("_").append(ServiceType.valueOf(rplTask.getType()))
            .append("_").append(rplTask.getId());
        return sb.toString();
    }

    public static String getRplInitialPosition() {
        return "0:4#0.0";
    }

    public static List<MutableTriple<String, Integer, String>> getComputeNodes(Connection connection) {
        List<MutableTriple<String, Integer, String>> computeNodes = new ArrayList<>();
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery(SHOW_COMPUTE_NODE);
            while (rs.next()) {
                String host = rs.getString(1);
                Integer port = rs.getInt(2);
                String instId = rs.getString(3);
                computeNodes.add(new MutableTriple<>(host, port, instId));
            }
        } catch (SQLException e) {
            throw new PolardbxException("connect to master failed ", e);
        }
        return computeNodes;
    }

    public static List<MutableTriple<String, Integer, String>> getComputeNodesWithFixedInstId(Connection connection,
                                                                                              String instId) {
        List<MutableTriple<String, Integer, String>> computeNodes = getComputeNodes(connection);
        return computeNodes.stream().filter(triple -> instId.equals(triple.getRight())).collect(Collectors.toList());
    }

    public static List<MutablePair<String, Integer>> getDumperNodes(Connection connection) {
        return null;
    }

    public static List<String> getStreamLatestPositions(Connection connection, String streamGroupName) {
        List<String> positions = new ArrayList<>();
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery(SHOW_BINARY_STREAMS);
            while (rs.next()) {
                String groupName = rs.getString("GROUP");
                if (StringUtils.equals(groupName, streamGroupName)) {
                    String file = rs.getString("FILE");
                    String offset = rs.getString("POSITION");
                    positions.add(file + ":" + offset);
                }
            }
        } catch (SQLException e) {
            throw new PolardbxException("connect to master failed ", e);
        }
        if (positions.isEmpty()) {
            throw new PolardbxException("can not find position for this stream " + streamGroupName);
        }
        return positions;
    }

    public static String getBinaryLatestPosition(Connection connection) {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery(SHOW_MASTER_STATUS);
            if (rs.next()) {
                String file = rs.getString("FILE");
                String offset = rs.getString("POSITION");
                return file + ":" + offset;
            }
        } catch (SQLException e) {
            throw new PolardbxException("connect to master failed ", e);
        }
        throw new PolardbxException("can not get master latest position automatically");
    }

    public static PolardbxException waitAllTaskFinishedAndReturn(List<Future<Void>> futures) {
        Throwable throwable = null;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Throwable e) {
                log.warn("task execute runs into exception: ", e);
                throwable = e;
            }
        }
        if (throwable == null) {
            return null;
        }
        return new PolardbxException(throwable);
    }

    public static void hostSafeCheck(String host) {
        String regex = "^[a-zA-Z0-9_\\-.:\\[\\]]+$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(host);

        if (!matcher.matches()) {
            throw new PolardbxException(String.format("invalid host name : %s", host));
        }
    }

    public static <T> T getRandomElementFromList(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Random random = new Random();
        int index = random.nextInt(list.size());
        return list.get(index);
    }
}
