/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static com.aliyun.polardbx.binlog.ConfigKeys.DN_PASSWORD_KEY;

/**
 * @author wuheng.zxy
 */
@Slf4j
public class PasswdUtil {

    public static String decryptBase64(String sSrc) {
        String sKey = System.getenv(DN_PASSWORD_KEY);
        if (sKey == null) {
            sKey = DynamicApplicationConfig.getString(DN_PASSWORD_KEY);
            if (StringUtils.isBlank(sKey)) {
                return sSrc;
            }
        }
        return decryptBase64(sSrc, sKey);
    }

    public static String decryptBase64(String sSrc, String sKey) {
        try {
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            //先用base64解密
            byte[] encrypted1 = Base64.getDecoder().decode(sSrc);
            byte[] original = cipher.doFinal(encrypted1);
            String originalString = new String(original, "utf-8");
            return originalString;
        } catch (Exception ex) {
            throw new RuntimeException("param error during decrypt", ex);
        }
    }

    private static String byte2hex(byte[] b) {
        StringBuilder hs = new StringBuilder();
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            stmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
            if (stmp.length() == 1) {
                hs = hs.append("0" + stmp);
            } else {
                hs = hs.append(stmp);
            }
        }
        return hs.toString();
    }
}
