/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.filter;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * created by ziyang.lb
 **/

public class URLScanner {
    private final Class<? extends Annotation> annotationClazz;
    private final String basePackage;

    public URLScanner(Class<? extends Annotation> annotationClazz, String baskPackage) {
        this.annotationClazz = annotationClazz;
        this.basePackage = baskPackage;
    }

    public Set<URLMatcher> scan() {
        try {
            Set<URLMatcher> urlSets = new HashSet<>();
            ClassPathScanningCandidateComponentProvider scanningCandidateComponentProvider =
                new ClassPathScanningCandidateComponentProvider(false);
            scanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(Path.class));
            Set<BeanDefinition> definitionSet = scanningCandidateComponentProvider.findCandidateComponents(basePackage);

            for (BeanDefinition definition : definitionSet) {
                Class<?> cls = Class.forName(definition.getBeanClassName());

                Path path = cls.getAnnotation(Path.class);
                String parentPath;
                if (path != null) {
                    parentPath = path.value();
                } else {
                    continue;
                }

                Object parentAnnotation = cls.getAnnotation(annotationClazz);

                Method[] methods = cls.getDeclaredMethods();
                for (Method m : methods) {
                    Object methodAnnotation = m.getAnnotation(annotationClazz);
                    if (parentAnnotation != null || methodAnnotation != null) {
                        Path subPath = m.getAnnotation(Path.class);
                        if (subPath == null) {
                            continue;
                        }
                        String urlPath = fillForwardSlash(parentPath) + fillForwardSlash(subPath.value());
                        urlSets.add(new URLMatcher(urlPath));
                    }
                }
            }
            return urlSets;
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    private String fillForwardSlash(String path) {
        if (!StringUtils.startsWith(path, "/")) {
            return "/" + path;
        }
        return path;
    }
}
