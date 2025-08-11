/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlExprParser;
import com.alibaba.polardbx.druid.sql.parser.ByteString;
import com.alibaba.polardbx.druid.sql.parser.Lexer;
import com.alibaba.polardbx.druid.sql.parser.Token;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.rpl.common.RplConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author jiyue 2021/8/17 13:28
 * @since 5.0.0.0
 */

public class BaseFilter {
    public void init() {
    }

    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        return false;
    }

    public boolean ignoreEventByTso(String tso) {
        return false;
    }

    public String getRewriteDb(String schema, DBMSAction action) {
        return schema;
    }

    public String getRewriteTable(String db, String table) {
        return table;
    }

    protected List<String> getWords(String filterStr) {
        List<String> wordList = new ArrayList<>();
        final MySqlExprParser exprParser = new MySqlExprParser(ByteString.from(filterStr), false);
        final Lexer lexer = exprParser.getLexer();
        Token token = null;
        while (token != Token.EOF) {
            token = lexer.token();
            if (token == Token.IDENTIFIER) {
                wordList.add(SQLUtils.normalizeNoTrim(lexer.stringVal()));
            }
            lexer.nextToken();
        }
        return wordList;
    }

    protected Set<String> initFilterSet(String filterStr) {
        if (StringUtils.isNotBlank(filterStr)) {
            return new HashSet<>(getWords(filterStr));
        }
        return new HashSet<>();
    }

    protected Set<String> initFilterNums(String filterStr) {
        Set<String> filters = new HashSet<>();
        if (StringUtils.isNotBlank(filterStr)) {
            for (String token : filterStr.trim().split(RplConstants.COMMA)) {
                filters.add(token.trim());
            }
        }
        return filters;
    }

    protected Set<Pair<String, String>> initFilterPairSet(String filterStr) {
        Set<Pair<String, String>> pairSet = new HashSet<>();
        if (StringUtils.isNotBlank(filterStr)) {
            List<String> words = getWords(filterStr);
            for (int i = 0; i < words.size() / 2; i++) {
                pairSet.add(Pair.of(words.get(2 * i), words.get(2 * i + 1)));
            }
        }
        return pairSet;
    }

    protected Set<Long> initIgnoreServerIds(String filterStr) {
        Set<String> tmpIgnoreServerIds = initFilterNums(filterStr);
        Set<Long> ignoreServerIds = new HashSet<>();
        for (String serverId : tmpIgnoreServerIds) {
            ignoreServerIds.add(Long.valueOf(serverId));
        }
        return ignoreServerIds;
    }

}
