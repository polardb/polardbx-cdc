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
