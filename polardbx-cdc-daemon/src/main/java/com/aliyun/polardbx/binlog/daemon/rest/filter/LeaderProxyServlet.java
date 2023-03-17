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

import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.proxy.ProxyServlet;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * Created by lubiao on 2017/4/16.
 */
public class LeaderProxyServlet extends ProxyServlet {
    private static final String _prefix = "/";

    @Override
    protected String rewriteTarget(HttpServletRequest clientRequest) {
        Pair<String, Integer> leaderInfo = RuntimeLeaderElector.getDaemonLeaderInfo();

        if (leaderInfo != null) {
            return rewriteTarget(clientRequest,
                "http://" + leaderInfo.getKey() + ":" + leaderInfo.getValue());
        }
        return null;
    }

    protected String rewriteTarget(HttpServletRequest request, String _proxyTo) {
        String path = request.getRequestURI();
        if (!path.startsWith(_prefix)) {
            return null;
        }

        StringBuilder uri = new StringBuilder(_proxyTo);
        if (_proxyTo.endsWith("/")) {
            uri.setLength(uri.length() - 1);
        }
        String rest = path.substring(_prefix.length());
        if (!rest.isEmpty()) {
            if (!rest.startsWith("/")) {
                uri.append("/");
            }
            uri.append(rest);
        }

        String query = request.getQueryString();
        if (query != null) {
            // Is there at least one path segment ?
            String separator = "://";
            if (uri.indexOf("/", uri.indexOf(separator) + separator.length()) < 0) {
                uri.append("/");
            }
            uri.append("?").append(query);
        }
        URI rewrittenURI = URI.create(uri.toString()).normalize();

        return rewrittenURI.toString();
    }
}
