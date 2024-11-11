/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

/**
 * 多线程执行器模板代码，otter中好多地方都写多线程，比较多的都是重复的逻辑代码，抽象一下做个模板把
 *
 * <pre>
 * 示例代码：
 * ExecutorTemplate template = new ExecutorTemplate(executor);
 * ...
 * try {
 *    for ( ....) {
 *       template.submit(new Runnable() {})
 *    }
 *
 *    List<?> result = template.waitForResult();
 *    // do result
 * } finally {
 *    template.clear();
 * }
 *
 * 注意：该模板工程，不支持多业务并发调用，会出现数据混乱
 * </pre>
 *
 * @author jianghang 2013-2-26 下午10:46:43
 */
public class ExecutorTemplate {

    private volatile ExecutorCompletionService completionService = null;
    private volatile List<Future> futures = null;

    public ExecutorTemplate(Executor executor) {
        completionService = new ExecutorCompletionService(executor);
        futures = Collections.synchronizedList(new ArrayList<Future>());
    }

    public void submit(Callable task) {
        Future future = completionService.submit(task);
        futures.add(future);
        check(future);// 立即check下，fast-fail
    }

    public void submit(Runnable task) {
        Future future = completionService.submit(task, null);
        futures.add(future);
        check(future);// 立即check下，fast-fail
    }

    private void check(Future future) {
        if (future.isDone()) {
            // 立即判断一次，因为使用了CallerRun可能当场跑出结果，针对有异常时快速响应，而不是等跑完所有的才抛异常
            try {
                future.get();
            } catch (InterruptedException e) {
                cancel();// 取消完之后立马退出
                throw new RuntimeException(e);
            } catch (Throwable e) {
                cancel(); // 取消完之后立马退出
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized List<?> waitForResult() {
        List result = new ArrayList();
        RuntimeException exception = null;
        // 开始处理结果
        int index = 0;
        while (index < futures.size()) { // 循环处理发出去的所有任务
            try {
                Future future = completionService.take();// 它也可能被打断
                result.add(future.get());
            } catch (InterruptedException e) {
                exception = new RuntimeException(e);
                break;// 如何一个future出现了异常，就退出
            } catch (Throwable e) {
                exception = new RuntimeException(e);
                break;// 如何一个future出现了异常，就退出
            }

            index++;
        }

        if (exception != null) {
            // 小于代表有错误，需要对未完成的记录进行cancel操作，对已完成的结果进行收集，做重复录入过滤记录
            cancel();
            throw exception;
        } else {
            return result;
        }
    }

    public void cancel() {
        for (int i = 0; i < futures.size(); i++) {
            Future future = futures.get(i);
            if (!future.isDone() && !future.isCancelled()) {
                future.cancel(true);
            }
        }
    }

    public void clear() {
        futures.clear();
    }
}
