/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.util.CharsetCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author chengjin.lyf on 2020/7/17 5:50 下午
 * @since 1.0.25
 */
@Slf4j
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
    public static final String SYNC_POINT_PROCEDURE_NAME = "trigger_sync_point_trx";
    public static final String SYNC_POINT_PRIVATE_DDL_SQL =
        "CALL " + SYNC_POINT_PROCEDURE_NAME;

    private static final String XID_FLAG_NORMAL = "1";
    private static final String XID_FLAG_ARCHIVE = "3";
    //普通事务，使用新的 share read view 机制
    private static final String XID_NEW_FLAG_10001 = "10001";
    //事务日志下沉的事务，使用新的 share read view 机制
    private static final String XID_NEW_FLAG_10002 = "10002";
    //异步提交事务，使用新的 share read view 机制
    private static final String XID_NEW_FLAG_10003 = "10003";

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
        return XID_FLAG_NORMAL.equals(flag) || XID_FLAG_ARCHIVE.equals(flag) || XID_NEW_FLAG_10001.equals(flag)
            || XID_NEW_FLAG_10002.equals(flag) || XID_NEW_FLAG_10003.equals(flag);
    }

    public static boolean isArchiveXid(String xid) {
        String flag = StringUtils.substringAfterLast(xid, ",");
        return XID_FLAG_ARCHIVE.equals(flag);
    }

    public static Long getTranIdFromXid(String xid, String encoding) throws Exception {
        return processTranId(StringUtils.substringBefore(xid, ","), encoding);
    }

    public static String getHexTranIdFromXid(String xid, String encoding) throws Exception {
        xid = StringUtils.substringBefore(xid, ",");
        String hexTid = new String(Hex.decodeHex(unwrap(xid)), CharsetCache.lookup(encoding));
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
        Charset charset = CharsetCache.lookup(encoding);
        return new String(Hex.decodeHex(unwrap(StringUtils.substringBefore(partTwo, ","))), charset);
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
        String hexTid = new String(Hex.decodeHex(unwrap(xid)), CharsetCache.lookup(charset));
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
     * trace： 事务 id
     * seq: 逻辑sql id
     * subseq：物理sql id
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
            String[] results = new String[4];
            Scanner scanner = new Scanner(query);
            scanner.useDelimiter("/");
            int index = 0;
            String seq = null;
            String subSeq = null;
            String serverId = null;
            String markCode = null;
            while (scanner.hasNext()) {
                String keyWord = StringUtils.trim(scanner.next());

                if (index == 1) {
                    // trace-seq
                    String[] secondarySplitArray = StringUtils.split(keyWord, "-");
                    seq = secondarySplitArray.length < 2 ? "0" : secondarySplitArray[1];
                }

                if (index == 2) {
                    // subseq
                    if (NumberUtils.isCreatable(keyWord)) {
                        subSeq = keyWord;
                    }
                }

                if (index == 3) {
                    // serverid
                    if (NumberUtils.isCreatable(keyWord)) {
                        serverId = keyWord;
                    }
                }

                // replace returning or insert ignore 可能会有insert/update比delete先执行的情况，但为了保证uk的唯一性，在binlog内不能这样做
                // binlog内的顺序应该是delete 比 insert先做，因此，先执行的insert的subSeq
                // 所以加个标代表这个trace id的顺序是乱序的，不进行检查
                if (index == 8) {
                    // markCode
                    if (NumberUtils.isCreatable(keyWord)) {
                        markCode = keyWord;
                    }
                }
                index++;
            }

            String trace = buildTraceId(seq, subSeq);
            results[0] = trace;
            results[1] = serverId;
            results[2] = markCode;
            results[3] = seq;
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

    public static int getLogicSqlIdFromTraceId(String traceId) {
        return Integer.parseInt(traceId.substring(TRACE_MAIN_LEN));
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
        if (rowsQueryLog == null) {
            return null;
        }

        // 使用字符串查找替换Scanner，性能更好
        int ctsIndex = rowsQueryLog.indexOf("CTS");
        if (ctsIndex == -1) {
            return null;
        }

        int beginIndex = rowsQueryLog.indexOf("::", ctsIndex);
        if (beginIndex == -1) {
            return null;
        }

        beginIndex += 2; // 跳过 "::"

        // 查找下一个 "::" 作为结束位置
        int endIndex = rowsQueryLog.indexOf("::", beginIndex);

        if (endIndex != -1) {
            // 存在下一个 "::"，提取中间部分
            return rowsQueryLog.substring(beginIndex, endIndex);
        } else {
            // 没有下一个 "::"，提取到字符串末尾
            return rowsQueryLog.substring(beginIndex);
        }
    }

    public static String makeXid(Long tranId, String groupName) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        sb.append("X'")
            .append(Hex.encodeHex((LogEventUtil.DRDS_TRAN_PREFIX + Long.toHexString(tranId) + "@1")
                .getBytes(StandardCharsets.UTF_8)))
            .append("','")
            .append(Hex.encodeHex(groupName.getBytes(StandardCharsets.UTF_8)))
            .append("',1");
        return sb.toString();
    }
}
