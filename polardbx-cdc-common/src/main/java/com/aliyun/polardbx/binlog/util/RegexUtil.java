/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yudong
 * @since 2023/10/11 14:41
 **/
public class RegexUtil {
    private static final String SPLIT = ",";

    private static final LoadingCache<String, List<Pattern>> CACHE = CacheBuilder.newBuilder()
        .maximumSize(2048)
        .expireAfterAccess(120, TimeUnit.SECONDS)
        .build(new CacheLoader<String, List<Pattern>>() {
            @Override
            public List<Pattern> load(String regexString) {
                return RegexUtil.convert2Patterns(regexString);
            }
        });

    public static Boolean match(String regexString, String eventString) {

        if (StringUtils.isBlank(regexString)) {
            return false;
        }

        return match(CACHE.getUnchecked(regexString), eventString);
    }

    public static Boolean match(List<Pattern> patterns, String eventString) {

        if (CollectionUtils.isEmpty(patterns)) {
            return false;
        }

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(eventString.toLowerCase());
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    public static List<Pattern> convert2Patterns(String regexString) {
        List<Pattern> patterns = new ArrayList<>();

        if (StringUtils.isNotBlank(regexString)) {
            String[] split = regexString.split(SPLIT);
            for (String s : split) {
                patterns.add(Pattern.compile(s, Pattern.DOTALL));
            }
        }
        return patterns;
    }
}
