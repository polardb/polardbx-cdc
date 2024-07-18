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
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

/**
 * This class creates a default SQL column implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class AnonymousColumn extends DBMSColumn {

    private static final long serialVersionUID = -5681302414718582726L;

    // Type assignment from java.sql.Types
    protected int ordinalIndex;
    protected int sqlType;
    protected int meta;

    public AnonymousColumn(int ordinalIndex, int sqlType, int meta) {
        this.ordinalIndex = ordinalIndex;
        this.sqlType = sqlType;
        this.meta = meta;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getOrdinalIndex() {
        return ordinalIndex;
    }

    @Override
    public int getSqlType() {
        return sqlType;
    }

    public int getMeta() {
        return meta;
    }

    @Override
    public boolean isSigned() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean isPrimaryKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUniqueKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGenerated() {
        return false;
    }

    @Override
    public boolean isRdsImplicitPk() {
        return false;
    }
}
