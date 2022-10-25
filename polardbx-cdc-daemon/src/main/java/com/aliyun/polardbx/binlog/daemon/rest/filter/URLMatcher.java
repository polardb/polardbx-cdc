/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.daemon.rest.filter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class URLMatcher {
    private String path;
    private List<KeyWorld> keyWorldsList;

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
            if (keyWorldsList.size() < index) {
                return false;
            }
            if (!keyWorldsList.get(index++).match(kw)) {
                return false;
            }
        }
        return keyWorldsList.size() == index;
    }

    private void init() {
        String[] keyWorlds = path.split("/");
        keyWorldsList = new ArrayList(keyWorlds.length);
        for (String kw : keyWorlds) {
            if (StringUtils.isBlank(kw)) {
                continue;
            }
            if (kw.startsWith("{") || kw.endsWith("}")) {
                keyWorldsList.add(new KeyWorld(kw, true));
            } else {
                keyWorldsList.add(new KeyWorld(kw));
            }
        }
    }

    static class KeyWorld {
        String pattern;
        boolean ignore = false;

        public KeyWorld(String pattern, boolean ignore) {
            this.pattern = pattern;
            this.ignore = ignore;
        }

        public KeyWorld(String pattern) {
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
