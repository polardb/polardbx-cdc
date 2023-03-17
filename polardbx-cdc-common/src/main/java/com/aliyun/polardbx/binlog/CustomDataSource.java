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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.PropertyChangeListener;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * custom datasource for special need
 * <p>
 *
 * @author Created by ziyang.lb
 **/
public class CustomDataSource extends DataSource implements PropertyChangeListener, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(CustomDataSource.class);
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

    @Override
    public void onInit(String propsName, String value) {

    }

    @Override
    public void onPropertyChange(String propsName, String oldValue, String newValue) {
        synchronized (this) {
            setUrl(newValue);
            close(true);
            logger.warn("detected meta db url change , new url:" + newValue);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DynamicApplicationConfig.addPropListener(ConfigKeys.METADB_URL, this);
    }
}
