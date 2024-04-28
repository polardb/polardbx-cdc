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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class SpringContextBootStrap {

    private final String filePath;
    private static final AtomicBoolean booted = new AtomicBoolean(false);
    @Getter
    private ClassPathXmlApplicationContext applicationContext;

    public SpringContextBootStrap(String filePath) {
        this.filePath = filePath;
    }

    public void boot() {
        if (booted.compareAndSet(false, true)) {
            try {
                prepare();
                applicationContext = new ClassPathXmlApplicationContext(filePath) {

                    @Override
                    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
                        super.customizeBeanFactory(beanFactory);
                        beanFactory.setAllowBeanDefinitionOverriding(false);
                    }
                };
            } catch (Throwable e) {
                throw new PolardbxException("ERROR ## spring application context initial failed.", e);
            }
        }
    }

    public void close() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    public void prepare() {
        // set some variables for lab test
        if (StringUtils.equalsIgnoreCase("true", System.getenv("is_lab_env"))) {
            System.setProperty("binlogx_table_level_hash_table_list_regex",
                ".*\\.modify_im_pk.*,.*\\.modify_pk.*,.*\\.modify_sk.*,.*\\.test_ddl_interruption.*");
            log.info("lab env: binlogx_table_level_hash_table_list_regex = " + System.getProperty(
                "binlogx_table_level_hash_table_list_regex"));

            System.setProperty("task_extract_filter_physical_table_blacklist",
                "cdc_blacklist_db_.*\\.cdc_black_table.*");
            log.info("lab env: task_extract_filter_physical_table_blacklist = " + System.getProperty(
                "task_extract_filter_physical_table_blacklist"));

            System.setProperty("latest_server_address_persist_file", "/home/admin/logs/latest_server_addr.json");
            log.info("lab env: latest_server_address_persist_file = " + System.getProperty(
                "latest_server_address_persist_file"));
        }
    }
}
