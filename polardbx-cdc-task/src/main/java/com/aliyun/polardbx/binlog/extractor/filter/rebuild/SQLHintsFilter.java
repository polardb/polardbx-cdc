/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.sql.ast.SQLCommentHint;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.TDDLHint;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_HINT_BLACKLIST;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-09-13 18:51
 **/
@Slf4j
public class SQLHintsFilter {

    public static void filter(SQLStatement stmt) {
        try {
            doFilter(stmt);
        } catch (Throwable e) {
            log.error("filter sql hints error!! {}", stmt.toString(), e);
            throw e;
        }
    }

    private static void doFilter(SQLStatement stmt) {
        Set<String> hintsBlackList = getHintsBlackList();
        if (hintsBlackList.isEmpty()) {
            return;
        }

        List<SQLCommentHint> hintList = stmt.getHeadHintsDirect();
        if (hintList == null) {
            return;
        }
        for (SQLCommentHint hint : hintList) {
            if (!(hint instanceof TDDLHint)) {
                continue;
            }

            List<Pair<String, List<String>>> filterList = new ArrayList<>();
            TDDLHint tddlHint = (TDDLHint) hint;
            List<TDDLHint.Function> functions = tddlHint.getFunctions();
            for (TDDLHint.Function function : functions) {
                List<TDDLHint.Argument> arguments = function.getArguments();
                for (TDDLHint.Argument argument : arguments) {
                    if (argument.getName() == null || argument.getValue() == null) {
                        continue;
                    }

                    SQLIdentifierExpr exprKey = (SQLIdentifierExpr) argument.getName();
                    String keyForRemove = exprKey.getSimpleName();
                    if (hintsBlackList.contains(keyForRemove.toUpperCase())) {
                        if (argument.getValue() instanceof SQLCharExpr) {
                            String text = ((SQLCharExpr) argument.getValue()).getText();
                            filterList.add(Pair.of(keyForRemove,
                                Lists.newArrayList("\"" + text + "\"", "'" + text + "'")));
                        } else {
                            filterList.add(Pair.of(keyForRemove, Lists.newArrayList(argument.getValue().toString())));
                        }
                    }
                }
            }

            tddlHint.setText(rewriteText(tddlHint.getText(), filterList));
        }
    }

    private static Set<String> getHintsBlackList() {
        String config = getString(TASK_REFORMAT_DDL_HINT_BLACKLIST);
        if (StringUtils.isNotBlank(config)) {
            String[] array = StringUtils.split(config.toUpperCase(), ",");
            return Sets.newHashSet(array);
        }
        return Sets.newHashSet();
    }

    private static String rewriteText(String text, List<Pair<String, List<String>>> filterList) {
        if (filterList.isEmpty() || StringUtils.isBlank(text)) {
            return text;
        }

        for (Pair<String, List<String>> pair : filterList) {
            String keyForRemove = pair.getKey();
            String textHold = text;

            for (String valueForRemove : pair.getValue()) {
                String basePattern = Pattern.quote(keyForRemove) + "\\s*=\\s*" + Pattern.quote(valueForRemove);
                String pattern1 = ",\\s*" + basePattern;
                String pattern2 = basePattern + "\\s*,";

                String newText = replaceAll(text, pattern1, "");
                if (StringUtils.equals(text, newText)) {
                    newText = replaceAll(text, pattern2, "");
                    if (StringUtils.equals(text, newText)) {
                        newText = replaceAll(text, basePattern, "");
                    }
                }
                if (!StringUtils.equals(text, newText)) {
                    text = newText;
                    break;
                }
            }

            if (StringUtils.equals(textHold, text)) {
                log.warn("hints not matched for {} : {} : {}", text, keyForRemove, pair.getValue());
            }
        }

        return text;
    }

    private static String replaceAll(String text, String regex, String replacement) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text).replaceAll(replacement);
    }
}
