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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.util.PasswdUtil;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * custom datasource for special need
 * <p>
 *
 * @author Created by ziyang.lb
 **/
public class CustomDataSource extends DataSource {
    private final String dnPasswordKey;
    private final boolean useEncryptedPassword;

    public CustomDataSource(String dnPasswordKey, boolean useEncryptedPassword) {
        this.dnPasswordKey = dnPasswordKey;
        this.useEncryptedPassword = useEncryptedPassword;
    }

    @Override
    public void setPassword(String password) {
        if (useEncryptedPassword) {
            password = PasswdUtil.decryptBase64(password, dnPasswordKey);
        }
        super.setPassword(password);
    }

    public String getDnPasswordKey() {
        return dnPasswordKey;
    }
}
