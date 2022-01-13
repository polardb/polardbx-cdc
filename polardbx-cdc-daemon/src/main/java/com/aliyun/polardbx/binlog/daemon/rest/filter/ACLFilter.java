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

package com.aliyun.polardbx.binlog.daemon.rest.filter;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.daemon.rest.signature.Signature;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.catalina.filters.RequestFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.eclipse.jetty.server.Request;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.ws.rs.Path;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ACLFilter extends RequestFilter {

    public static final String AK = "tQMxr3M71ha3hHLudXGc6e4Gd7CQ59l7JL5kdyfo";
    private static final String SK = "tQMxr3M71ha3hHLudXGc6e4Gd7CQ59l7JL5kdyfo";
    private static final String ACCESS_KEY = "accessKey";
    private static final String SIGNATURE = "signature";
    private static final String TIMESTAMP = "timestamp";
    // 30s超时
    private static final long TIME_OUT = TimeUnit.SECONDS.toMillis(30);
    private static Map<String, String> akMap = new HashMap<>();
    private final Log log = LogFactory.getLog(ACLFilter.class);
    private Set<URLMatcher> urlSets = Sets.newHashSet();

    public ACLFilter(String basePackage) {
        try {
            ClassPathScanningCandidateComponentProvider scanningCandidateComponentProvider =
                new ClassPathScanningCandidateComponentProvider(false);
            scanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(ACL.class));
            Set<BeanDefinition> definitionSet = scanningCandidateComponentProvider.findCandidateComponents(basePackage);
            for (BeanDefinition definition : definitionSet) {
                log.info("init " + definition.getBeanClassName());
                Class cls = Class.forName(definition.getBeanClassName());
                Path path = (Path) cls.getAnnotation(Path.class);
                String parentPath;
                if (path != null) {
                    parentPath = path.value();
                } else {
                    parentPath = "";
                }
                Method[] methods = cls.getDeclaredMethods();
                for (Method m : methods) {
                    Path subPath = m.getAnnotation(Path.class);
                    if (subPath == null) {
                        continue;
                    }
                    String urlPath = parentPath + subPath.value();
                    urlSets.add(new URLMatcher(urlPath));
                }
            }

        } catch (Exception e) {
            throw new PolardbxException(e);
        }

        System.out.println(urlSets);
    }

    private Map<String, String> getRequestParams(ServletRequest request) {

        Enumeration<String> parameterNames = request.getParameterNames();

        Map<String, String> params = Maps.newTreeMap();
        if (parameterNames != null) {
            while (parameterNames.hasMoreElements()) {
                String paramKey = parameterNames.nextElement();
                if (!SIGNATURE.equals(paramKey)) {
                    String parameter = request.getParameter(paramKey);
                    params.put(paramKey, parameter);
                }
            }

        }
        return params;
    }

    private boolean isACL(String requestURL) {

        if (!DynamicApplicationConfig.getBoolean(ConfigKeys.ENABLE_INTERFACE_ACL)) {
            return false;
        }

        for (URLMatcher matcher : urlSets) {
            if (matcher.match(requestURL)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
        throws IOException, ServletException {
        String requestUrl = ((Request) request).getHttpURI().toString();

        if (!isACL(requestUrl)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.info("request " + requestUrl + " need authorized!");
        String ak = request.getParameter(ACCESS_KEY);
        String sign = request.getParameter(SIGNATURE);
        Map<String, String> params = getRequestParams(request);
        if (StringUtils.isNotEmpty(ak) && StringUtils.isNotEmpty(sign)) {
            if (checkSignature(request, params, SK)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        response.setContentType("text/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.write("no authorized");
        out.flush();
        out.close();
    }

    protected boolean checkSignature(ServletRequest request, Map<String, String> params, String secretKey) {
        if (StringUtils.isEmpty(secretKey)) {
            throw new PolardbxException("The user's accessKey not exists");
        }

        String timestamp = params.get(TIMESTAMP);
        if (!NumberUtils.isCreatable(timestamp)) {
            throw new PolardbxException("The timestamp not exists");
        }

        Long time = NumberUtils.createLong(timestamp);
        long diff = Math.abs(System.currentTimeMillis() - time);
        if (diff > TIME_OUT) {
            throw new PolardbxException("The request is time out");
        }
        String requestSignature = request.getParameter(SIGNATURE);
        if (StringUtils.isEmpty(requestSignature)) {
            throw new PolardbxException("there is no signature");
        }
        String calcSignature = null;
        try {
            calcSignature = Signature.doSignature(params, secretKey);
        } catch (Exception e) {
            throw new PolardbxException("can not calculate signature", e);
        }
        if (!requestSignature.equals(calcSignature)) {
            throw new PolardbxException("signature inconsist，request:" + requestSignature + ",calc:" + calcSignature);
        }
        return true;
    }

    @Override
    protected Log getLogger() {
        return log;
    }
}
