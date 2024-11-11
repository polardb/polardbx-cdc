/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;

public class JsonTest {
    @Data
    public static class MyTest {
        private Boolean a = true;
        private Integer b = 123;
    }

    @Test
    public void getValue() {
        MyTest test = JSONObject.parseObject("{\"b\":\"345\"}", MyTest.class);
        Assert.assertEquals(test.a, true);
    }

    public static class TestBean {

        String a;
        Boolean b = true;//这里给b设置了默认值，坑点

        public TestBean(boolean b) {
            this.b = b;
        }
    }

    @Test
    public void getValue2() {
        TestBean test = JSONObject.parseObject("{a:\"标题\"}", TestBean.class);
        Assert.assertEquals(test.b, false);
    }
}
