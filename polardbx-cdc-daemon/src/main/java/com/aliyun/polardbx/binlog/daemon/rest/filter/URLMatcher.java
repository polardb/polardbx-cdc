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
package com.aliyun.polardbx.binlog.daemon.rest.filter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class URLMatcher {
    private final String path;
    private List<KeyWord> keyWordsList;

    public URLMatcher(String path) {
        this.path = path;
        init();
    }

    public boolean match(String requestURI) {
        requestURI = requestURI.split("\\?")[0];
        String[] keyWorlds = requestURI.split("/");
        int index = 0;
        for (String kw : keyWorlds) {
            if (StringUtils.isBlank(kw)) {
                continue;
            }
            if (keyWordsList.size() < index) {
                return false;
            }
            if (!keyWordsList.get(index++).match(kw)) {
                return false;
            }
        }
        return keyWordsList.size() == index;
    }

    private void init() {
        String[] keyWords = path.split("/");
        keyWordsList = new ArrayList<>(keyWords.length);
        for (String kw : keyWords) {
            if (StringUtils.isBlank(kw)) {
                continue;
            }
            if (kw.startsWith("{") || kw.endsWith("}")) {
                keyWordsList.add(new KeyWord(kw, true));
            } else {
                keyWordsList.add(new KeyWord(kw));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        URLMatcher that = (URLMatcher) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    static class KeyWord {
        String pattern;
        boolean ignore = false;

        public KeyWord(String pattern, boolean ignore) {
            this.pattern = pattern;
            this.ignore = ignore;
        }

        public KeyWord(String pattern) {
            this.pattern = pattern;
        }

        public boolean match(String kw) {
            if (ignore) {
                return true;
            }
            return pattern.equalsIgnoreCase(kw);
        }
    }
}
