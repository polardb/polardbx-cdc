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
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.UnsupportedEncodingException;
import java.util.Scanner;

/**
 * @author chengjin.lyf on 2020/7/17 5:50 下午
 * @since 1.0.25
 */
public class LogEventUtil {

    /**
     * xa 分布式 query log
     */
    public static final String XA_START = "XA START";
    /**
     * xa 分布式 query MySqlFullExtractorProcessorlog
     */
    public static final String XA_COMMIT = "XA COMMIT";

    public static final String XA_ROLLBACK = "XA ROLLBACK";
    public static final String XA_END = "XA END";
    public static final String DRDS_TRAN_PREFIX = "drds-";
    public static final int TRACE_MAIN_LEN = 10;
    public static final int TRACE_SUB_LEN = 10;
    /**
     * 单机一阶段 query log
     */
    private static final String BEGIN = "BEGIN";
    private static final String COMMIT = "COMMIT";

    public static boolean isTransactionEvent(QueryLogEvent event) {
        String query = event.getQuery();
        return query.startsWith(BEGIN) || query.startsWith(COMMIT) || query.startsWith(XA_START) || query.startsWith(
            XA_COMMIT) || query.startsWith(XA_END) || query.startsWith(XA_ROLLBACK);
    }

    /**
     * DRDS xid 组成 格式 'drds-xxx','groupname',1
     */
    public static String getXid(LogEvent event) {
        if (event instanceof QueryLogEvent) {
            QueryLogEvent queryLogEvent = (QueryLogEvent) event;
            String query = queryLogEvent.getQuery();
            if (query.startsWith(XA_START)) {
                return query.substring(XA_START.length()).trim();
            }
            if (query.startsWith(XA_COMMIT)) {
                return query.substring(XA_COMMIT.length()).trim();
            }
            if (query.startsWith(XA_ROLLBACK)) {
                return query.substring(XA_ROLLBACK.length()).trim();
            }
        }
        return null;
    }

    public static boolean isValidXid(String xid) {
        String flag = StringUtils.substringAfterLast(xid, ",");
        return "1".equals(flag);
    }

    public static Long getTranIdFromXid(String xid, String encoding) throws Exception {
        return processTranId(StringUtils.substringBefore(xid, ","), encoding);
    }

    public static String getHexTranIdFromXid(String xid, String encoding) throws Exception {
        xid = StringUtils.substringBefore(xid, ",");
        String hexTid = new String(Hex.decodeHex(unwrap(xid)), encoding);
        hexTid = hexTid.substring(DRDS_TRAN_PREFIX.length());
        return hexTid.split("@")[0];
    }

    public static String getGroupFromXid(String xid, String encoding) throws Exception {
        String str = getGroupWithReadViewSeqFromXid(xid, encoding);
        return StringUtils.substringBefore(str, "@");
    }

    // 单靠group不能唯一标识一个事务提交分支
    // 在开启写并行策略时，一个group可以对应多个事务提交分支，此时需要通过 group + readViewSeq 来唯一标识一个事务提交分支
    public static String getGroupWithReadViewSeqFromXid(String xid, String encoding) throws Exception {
        String partTwo = StringUtils.substringAfter(xid, ",");
        return new String(Hex.decodeHex(unwrap(StringUtils.substringBefore(partTwo, ","))), encoding);
    }

    private static String unwrap(String str) {
        str = str.trim();
        int b = 0;
        int e = str.length();
        if (str.charAt(b) == 'X') {
            b += 1;
        }
        if (str.charAt(b) == '\'') {
            b += 1;
        }
        if (str.charAt(e - 1) == '\'') {
            e -= 1;
        }
        return str.substring(b, e);
    }

    private static Long processTranId(String xid, String charset) throws Exception {
        String hexTid = new String(Hex.decodeHex(unwrap(xid)), charset);
        hexTid = hexTid.substring(DRDS_TRAN_PREFIX.length());
        hexTid = hexTid.split("@")[0];
        return Long.parseLong(hexTid, 16);
    }

    public static boolean isHeartbeat(LogEvent event) {
        if (event instanceof SequenceLogEvent) {
            return ((SequenceLogEvent) event).isHeartbeat();
        }
        return false;
    }

    public static boolean validEventType(int eventType) {
        return LogEvent.START_EVENT_V3 <= eventType && eventType < LogEvent.ENUM_END_EVENT;
    }

    public static boolean isStart(LogEvent logEvent) {
        if (logEvent instanceof QueryLogEvent) {
            QueryLogEvent queryLogEvent = (QueryLogEvent) logEvent;
            if (queryLogEvent.getQuery().startsWith(XA_START)) {
                return true;
            }
            if (queryLogEvent.getQuery().startsWith(BEGIN)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCommit(LogEvent logEvent) {
        if (logEvent.getHeader().getType() == LogEvent.QUERY_EVENT) {
            String query = ((QueryLogEvent) logEvent).getQuery();
            if (query.startsWith(XA_COMMIT) || query.startsWith(COMMIT)) {
                return true;
            }
        }
        return logEvent.getHeader().getType() == LogEvent.XID_EVENT;
    }

    public static boolean containsPrepareGCN(LogEvent event) {
        return event.getHeader().getType() == LogEvent.QUERY_EVENT && ((QueryLogEvent) event).getPrepareGCN() != -1L;
    }

    public static boolean containsCommitGCN(LogEvent event) {
        return event.getHeader().getType() == LogEvent.QUERY_EVENT && ((QueryLogEvent) event).getCommitGCN() != -1L;
    }

    public static boolean isRollback(LogEvent logEvent) {
        if (logEvent.getHeader().getType() == LogEvent.QUERY_EVENT) {
            if (((QueryLogEvent) logEvent).getQuery().startsWith(XA_ROLLBACK)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnd(LogEvent logEvent) {
        if (logEvent.getHeader().getType() == LogEvent.QUERY_EVENT) {
            if (((QueryLogEvent) logEvent).getQuery().startsWith(XA_END)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPrepare(LogEvent logEvent) {
        return logEvent.getHeader().getType() == LogEvent.XA_PREPARE_LOG_EVENT;
    }

    public static boolean isSequenceEvent(LogEvent event) {
        return event.getHeader().getType() == LogEvent.SEQUENCE_EVENT;
    }

    public static boolean isGcnEvent(LogEvent event) {
        return event.getHeader().getType() == LogEvent.GCN_EVENT;
    }

    public static boolean isHaveCommitSequence(GcnLogEvent gcnLogEvent) {
        // 第一个bit位，目前恒为1
        // 第二个bit位，如果为1，代表外部传入了snapshot tso；但如果为0，并不意味着外部没有传入snapshot tso；并不是一个充要条件
        // 第三个bit位，如果为1，代表外部传入了commit tso; 如果为0，代表外部没有传入snapshot tso；是一个充要条件
        // 当第二个bit位为1或者第三个bit位为1时，认为该事务是一个TSO事务
        // 当第三个bit位为1时，认为该GCN中包含外部传入的commit sequence
        int flagSeed = 0x00000004;
        return ((flagSeed & gcnLogEvent.getFlag()) == flagSeed);
    }

    public static boolean isRowsQueryEvent(LogEvent event) {
        return event.getHeader().getType() == LogEvent.ROWS_QUERY_LOG_EVENT;
    }

    /**
     * DRDS / ip / trace-seq / subseq
     *
     * @return / 10 / 2/, serverId
     */
    public static String[] buildTrace(RowsQueryLogEvent event) {
        String query = event.getRowsQuery();
        if (query.startsWith("/*DRDS")) {
            int beginIdx = query.indexOf("/", 6);
            int endIdx = query.indexOf("*/");
            if (beginIdx < 0 || endIdx < 0) {
                return null;
            }
            query = query.substring(beginIdx + 1, endIdx);
            String[] results = new String[2];
            Scanner scanner = new Scanner(query);
            scanner.useDelimiter("/");
            int index = 0;
            String seq = null;
            String subSeq = null;
            String serverId = null;
            while (scanner.hasNext()) {
                String keyWorkd = StringUtils.trim(scanner.next());

                if (index == 1) {
                    // trace-seq
                    String[] secondarySplitArray = StringUtils.split(keyWorkd, "-");
                    seq = secondarySplitArray.length < 2 ? "0" : secondarySplitArray[1];
                }

                if (index == 2) {
                    // subseq
                    if (NumberUtils.isCreatable(keyWorkd)) {
                        subSeq = keyWorkd;
                    }
                }
                if (index == 3) {
                    // serverid
                    if (NumberUtils.isCreatable(keyWorkd)) {
                        serverId = keyWorkd;
                    }
                }
                index++;
            }

            String trace = buildTraceId(seq, subSeq);
            results[0] = trace;
            results[1] = serverId;
            return results;
        }
        return null;
    }

    public static String buildTraceId(String mainSeq, String subSeq) {
        mainSeq = StringUtils.isBlank(mainSeq) ? "0" : mainSeq;
        subSeq = StringUtils.isBlank(subSeq) ? "0" : subSeq;
        String main = StringUtils.leftPad(mainSeq, TRACE_MAIN_LEN, "0");
        String sub = StringUtils.leftPad(subSeq, TRACE_SUB_LEN, "0");
        return main + sub;
    }

    /**
     * / DRDS / ip / trace-seq / subseq / server id / * /
     *
     * @return / 10 / 2/
     */
    public static long getServerIdFromRowQuery(RowsQueryLogEvent event) {
        long serverId = 0L;
        String query = event.getRowsQuery();
        if (query.startsWith("/*DRDS")) {
            String[] ps = StringUtils.split(query, "/");
            final int serverIdIdx = 4;
            if (ps.length >= serverIdIdx + 2) {
                String serverIdStr = ps[serverIdIdx];
                try {
                    serverId = Long.parseLong(serverIdStr);
                } catch (Throwable e) {

                }
            }
        }
        return serverId;
    }

    /**
     * /drds xxx /
     * # CTS::12321321321
     */
    public static String getTsoFromRowQuery(String rowsQueryLog) {
        String query = rowsQueryLog;
        Scanner scanner = new Scanner(query);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            int cts = line.indexOf("CTS");
            if (cts != -1) {
                int begin = line.indexOf("::", cts);
                int end = line.indexOf("::", begin + 2);
                if (end != -1) {
                    return line.substring(begin + 2, end);
                } else {
                    return line.substring(begin + 2);
                }
            }

        }
        return null;
    }

    public static String makeXid(Long tranId, String groupName) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        sb.append("X'")
            .append(Hex.encodeHex((LogEventUtil.DRDS_TRAN_PREFIX + Long.toHexString(tranId) + "@1").getBytes(
                "UTF-8")))
            .append("','")
            .append(Hex.encodeHex(groupName.getBytes("UTF-8")))
            .append("',1");
        return sb.toString();
    }
}
