/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author chengjin.lyf on 2020/7/15 5:51 下午
 * @since 1.0.25
 */
public class HandlerContext {

    public static final String REBUILD_TX_BUFFER_ITERATOR = "REBUILD_TX_BUFFER_ITERATOR";

    private final boolean isInitalFilter;
    private final Class<? extends HandlerEvent> TClass;
    private final LogEventFilter logEventFilter;
    private HandlerContext next;
    private RuntimeContext runtimeContext;
    private boolean ignore = false;
    private Map<String, Object> objectMap = new HashMap<>();

    public HandlerContext(LogEventFilter logEventFilter) {
        this.logEventFilter = logEventFilter;
        TClass = getSuperClassGenricType(logEventFilter.getClass(), 0);
        isInitalFilter = logEventFilter instanceof InitialFilter;
    }

    /**
     * 通过反射,获得定义Class时声明的父类的范型参数的类型
     */
    public static Class getSuperClassGenricType(Class clazz, int index) throws IndexOutOfBoundsException {
        Type genType = clazz.getGenericInterfaces()[0];
        if (!(genType instanceof ParameterizedType)) {
            return Object.class;
        }
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (index >= params.length || index < 0) {
            return Object.class;
        }
        if (!(params[index] instanceof Class)) {
            return Object.class;
        }
        return (Class) params[index];
    }

    public void put(String key, Object obj) {
        this.objectMap.put(key, obj);
    }

    public Object get(String key) {
        return objectMap.get(key);
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public boolean canIgnore(Long tso) {
        Long barrier = runtimeContext.getDnTransferMaxTSOBarrier();
        if (barrier == null) {
            return false;
        }
        if (tso <= barrier) {
            return true;
        }
        runtimeContext.cleanDnTransferTSOBarrier();
        return false;
    }

    public void doNext(HandlerEvent event) throws Exception {
        if (next != null) {
            next.setRuntimeContext(runtimeContext);
        }
        if (!ignore) {
            if (TClass.isAssignableFrom(event.getClass())) {
                logEventFilter.handle(event, next);
            } else {
                if (next != null) {
                    next.doNext(event);
                }
            }
        }
        if (!ignore && next != null && next.isInitial()) {
            ignore = true;
        }
    }

    private boolean isInitial() {
        return isInitalFilter;
    }

    public HandlerContext getNext() {
        return next;
    }

    public void setNext(HandlerContext next) {
        this.next = next;
    }

    public void fireStop() {
        ignore = true;
        logEventFilter.onStop();
        if (next != null) {
            next.fireStop();
        }
    }

    public void fireStart() {
        logEventFilter.onStart(this);
        if (next != null) {
            next.setRuntimeContext(runtimeContext);
            next.fireStart();
        }
    }

    public void fireStartConsume() {
        logEventFilter.onStartConsume(this);
        if (next != null) {
            next.setRuntimeContext(runtimeContext);
            next.fireStartConsume();
        }
    }
}
