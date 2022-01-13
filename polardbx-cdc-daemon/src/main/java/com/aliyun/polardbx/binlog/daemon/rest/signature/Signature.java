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

package com.aliyun.polardbx.binlog.daemon.rest.signature;

import sun.misc.BASE64Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by hongxi.chx on 2017/8/9.
 * recode ShuGuang at manager2.0
 */
public class Signature {

    private static final String ALGORITHM = "HmacSHA1";
    private static final Charset charset = Charset.forName("UTF-8");

    private static Mac mac;

    static {
        try {
            mac = Mac.getInstance("HmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Signature.static init exception.", e);
        }
    }

    public static String getSignature(Map<String, String> params, String secretKey) {
        TreeMap<String, String> paramsTreeMap = new TreeMap<>((o1, o2) -> {
            return o1.compareTo(o2);
        });
        Iterator i$ = params.entrySet().iterator();

        while (i$.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry<String, String>) i$.next();
            //过滤ak,sk这些用于验证的param
            if (!entry.getKey().equals("signature") && !entry.getKey().equals("accessKey")) {
                paramsTreeMap.put(entry.getKey(), entry.getValue());
            }
        }

        return doSignature(paramsTreeMap, secretKey);
    }

    public static String doSignature(Map<String, String> requestParams, String secretKey) {
        try {
            StringBuilder e = new StringBuilder();
            Iterator i$ = requestParams.entrySet().iterator();

            while (i$.hasNext()) {
                Map.Entry entry = (Map.Entry) i$.next();
                e.append(entry.getValue());
            }

            return macSignature(e.toString(), secretKey);
        } catch (Exception var5) {
            throw new RuntimeException("Signature.doSignature exception.", var5);
        }
    }

    private static synchronized String macSignature(String text, String secretKey) throws InvalidKeyException,
        NoSuchAlgorithmException {
        mac.init(new SecretKeySpec(secretKey.getBytes(charset), ALGORITHM));
        byte[] bytes = mac.doFinal(text.getBytes(charset));
        return (new BASE64Encoder()).encode(bytes);
    }

}
