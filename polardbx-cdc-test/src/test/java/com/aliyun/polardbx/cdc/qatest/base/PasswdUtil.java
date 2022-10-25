/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.aliyun.polardbx.binlog.util.TestConfig;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * @author chenghui.lch
 */
public class PasswdUtil {

    /**
     * Important Notice: The value of key can not be change!!
     * It is used by AES for encrypt & decrypt
     */
    private static String key = "";  // 128 bit key

    static {
        key = TestConfig.getConfig(PasswdUtil.class, "dnPasswordKey");
    }

    // Encode, AES + Base64
    public static String encrypt(String sSrc) {
        try {
            String sKey = key;
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(sSrc.getBytes("utf-8"));
            return Base64.getEncoder().encodeToString(encrypted);//此处使用BASE64做转码功能，同时能起到2次加密的作用。
        } catch (Throwable ex) {
            throw new RuntimeException("param error during encrypt", ex);
        }
    }

    // Decode, AES + Base64
    public static String decrypt(String sSrc) {
        try {
            String sKey = key;
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = Base64.getDecoder().decode(sSrc);//先用base64解密
            byte[] original = cipher.doFinal(encrypted1);
            String originalString = new String(original, "utf-8");
            return originalString;
        } catch (Exception ex) {
            throw new RuntimeException("param error during decrypt", ex);
        }
    }
}
