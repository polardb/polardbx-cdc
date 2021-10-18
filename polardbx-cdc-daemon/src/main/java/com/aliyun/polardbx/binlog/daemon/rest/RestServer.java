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

package com.aliyun.polardbx.binlog.daemon.rest;

import com.alibaba.fastjson.support.jaxrs.FastJsonProvider;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.daemon.rest.errors.RestExceptionMapper;
import com.aliyun.polardbx.binlog.daemon.rest.resources.EventsResource;
import com.aliyun.polardbx.binlog.daemon.rest.resources.MetricsResource;
import com.aliyun.polardbx.binlog.daemon.rest.resources.RootResource;
import com.aliyun.polardbx.binlog.daemon.rest.resources.SystemControlResource;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Embedded server for the REST API that provides the control plane for Datalink workers.
 */
public class RestServer {
    private static final Logger log = LoggerFactory.getLogger(RestServer.class);

    private static final long GRACEFUL_SHUTDOWN_TIMEOUT_MS = 60 * 1000;

    private Server jettyServer;

    /**
     * Create a REST server for this keeper using the specified configs.
     */
    public RestServer() {
        jettyServer = new Server();
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        jettyServer.setConnectors(new Connector[] {connector});
    }

    public static String urlJoin(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        } else {
            return base + path;
        }
    }

    public void start() {
        log.info("Starting REST server");

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.packages("com.aliyun.polardbx.binlog.daemon.rest.resources");
        resourceConfig.register(new FastJsonProvider());
        resourceConfig.register(RootResource.class);
        resourceConfig.register(RestExceptionMapper.class);
        resourceConfig.register(SystemControlResource.class);
        resourceConfig.register(MetricsResource.class);
        resourceConfig.register(EventsResource.class);

        ServletContainer servletContainer = new ServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(servletHolder, "/*");

        String allowedOrigins = "";
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
            filterHolder.setName("cross-origin");
            filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, allowedOrigins);
            String allowedMethods = "";
            if (allowedMethods != null && !allowedOrigins.trim().isEmpty()) {
                filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, allowedMethods);
            }
            context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
        }

        RequestLogHandler requestLogHandler = new RequestLogHandler();
        Slf4jRequestLog requestLog = new Slf4jRequestLog();
        requestLog.setLoggerName(RestServer.class.getCanonicalName());
        requestLog.setLogLatency(true);
        requestLogHandler.setRequestLog(requestLog);

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[] {context, new DefaultHandler(), requestLogHandler});

        /* Needed for graceful shutdown as per `setStopTimeout` documentation */
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);
        jettyServer.setHandler(statsHandler);
        jettyServer.setStopTimeout(GRACEFUL_SHUTDOWN_TIMEOUT_MS);
        jettyServer.setStopAtShutdown(true);

        try {
            jettyServer.start();
        } catch (Exception e) {
            throw new PolardbxException("Unable to start REST server", e);
        }

        log.info("REST server listening at " + jettyServer.getURI());
    }

    public void stop() {
        log.info("Stopping REST server");

        try {
            jettyServer.stop();
            jettyServer.join();
        } catch (Exception e) {
            throw new PolardbxException("Unable to stop REST server", e);
        } finally {
            jettyServer.destroy();
        }

        log.info("REST server stopped");
    }

    public static class HttpResponse<T> {
        private int status;
        private Map<String, List<String>> headers;
        private T body;

        public HttpResponse(int status, Map<String, List<String>> headers, T body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public Map<String, List<String>> headers() {
            return headers;
        }

        public T body() {
            return body;
        }
    }

    public static void main(String[] args) {
        // spring context
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();

        //rest server
        RestServer restServer = new RestServer();
        restServer.start();
    }
}