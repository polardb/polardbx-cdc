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

package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.UnsupportedEncodingException;

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

        if (event instanceof XaPrepareLogEvent) {
            XaPrepareLogEvent xaPrepareLogEvent = (XaPrepareLogEvent) event;
        }
        return null;
    }

    public static Long getTranIdFromXid(String xid, String encoding) throws Exception {
        String[] xidArgs = xid.split(",");
        return processTranId(xidArgs[0], encoding);
    }

    public static String getGroupFromXid(String xid, String encoding) throws Exception {
        String[] xidArgs = xid.split(",");
        return new String(Hex.decodeHex(unwrap(xidArgs[1])), encoding);
    }

    public static void main(String[] args) throws Exception {
        System.out.println(getGroupFromXid(
            "X'647264732d313236373530636261653030383030314062623236613963383163636433386265',X'5f5f4344435f5f5f3030303030305f47524f5550',1",
            "UTF-8"));
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

    public static boolean isGCNEvent(LogEvent event) {
        return event.getHeader().getType() == LogEvent.GCN_EVENT;
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
            String[] results = new String[2];
            String[] primarySplitArray = StringUtils.split(query, "/");
            String[] secondarySplitArray = StringUtils.split(primarySplitArray[2], "-");
            String seq = secondarySplitArray.length < 2 ? "0" : secondarySplitArray[1];
            String subSeq = null;
            if (NumberUtils.isCreatable(primarySplitArray[3])) {
                subSeq = primarySplitArray[3];
            }
            String trace = buildTraceId(seq, subSeq);
            results[0] = trace;
            if (primarySplitArray.length > 4) {
                results[1] = primarySplitArray[4];
            }
            return results;
        }
        return null;
    }

    public static String buildTraceId(String mainSeq, String subSeq) {
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

    public static String makeXid(Long tranId, String groupName) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        sb.append("X'")
            .append(Hex.encodeHex((LogEventUtil.DRDS_TRAN_PREFIX + Long.toHexString(tranId) + "@1").getBytes(
                "UTF-8")))
            .append("','")
            .append(Hex.encodeHex(groupName.getBytes("UTF-8")))
            .append("'");
        return sb.toString();
    }
}
