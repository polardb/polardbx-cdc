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
package com.aliyun.polardbx.rpl.validation;

import org.junit.Test;

import java.math.BigInteger;

/**
 * @author yudong
 * @since 2024/3/7 14:16
 **/
public class CompareTest {

    @Test
    public void test() {
        String k1 = "816085814676660224";
        String k2 = "816085814676660225";

        BigInteger v1 = new BigInteger(k1);
        BigInteger v2 = new BigInteger(k2);

        System.out.println(v1.compareTo(v2));
    }
}
