/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
