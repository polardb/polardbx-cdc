/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.filter;

import com.aliyun.polardbx.binlog.daemon.rest.ann.Leader;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;

/**
 * Created by lubiao on 2017/4/16.
 */
@Slf4j
public class LeaderCheckFilter implements Filter {

    private FilterConfig filterConfig;
    private LeaderProxyServlet proxyServlet;
    private final Set<URLMatcher> urlSets;

    public LeaderCheckFilter() {
        URLScanner scanner = new URLScanner(Leader.class, "com.aliyun.polardbx.binlog.daemon.rest.resources");
        urlSets = scanner.scan();
        log.info("url set for leader control is " + urlSets);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        this.proxyServlet = new LeaderProxyServlet();
        this.proxyServlet.init(new ServletConfig() {
            @Override
            public String getServletName() {
                return LeaderProxyServlet.class.getSimpleName();
            }

            @Override
            public ServletContext getServletContext() {
                return LeaderCheckFilter.this.filterConfig.getServletContext();
            }

            @Override
            public String getInitParameter(String name) {
                return LeaderCheckFilter.this.filterConfig.getInitParameter(name);
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return LeaderCheckFilter.this.filterConfig.getInitParameterNames();
            }
        });
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
        if (!shouldLeader(servletRequest) || RuntimeLeaderElector.isDaemonLeader()) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            proxyServlet.service(servletRequest, servletResponse);
            log.info("request is routed to leader, path is " + ((Request) servletRequest).getRequestURI());
        }
    }

    @Override
    public void destroy() {
        this.proxyServlet.destroy();
    }

    private boolean shouldLeader(ServletRequest servletRequest) {
        String requestUrl = ((Request) servletRequest).getRequestURI();
        for (URLMatcher matcher : urlSets) {
            if (matcher.match(requestUrl)) {
                return true;
            }
        }
        return false;
    }
}
