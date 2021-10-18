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
