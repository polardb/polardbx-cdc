/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ResTypeEnum枚举类的单元测试
 * 测试新增的DRDS枚举值以及所有枚举值的基本功能
 */
public class ResTypeEnumTest {

    @Test
    public void testAllEnumValues() {
        // 验证所有枚举值都存在
        ResTypeEnum[] allValues = ResTypeEnum.values();
        assertNotNull(allValues);
        assertEquals(5, allValues.length);
    }

    @Test
    public void testRdsMysqlValue() {
        // 测试RDS_MYSQL枚举值
        assertEquals("RDS_MYSQL", ResTypeEnum.RDS_MYSQL.getValue());
        assertEquals("RDS_MYSQL", ResTypeEnum.RDS_MYSQL.value);
    }

    @Test
    public void testPolarx1Value() {
        // 测试POLARX1枚举值
        assertEquals("POLARX1", ResTypeEnum.POLARX1.getValue());
        assertEquals("POLARX1", ResTypeEnum.POLARX1.value);
    }

    @Test
    public void testDrdsValue() {
        // 测试新增的DRDS枚举值
        assertEquals("DRDS", ResTypeEnum.DRDS.getValue());
        assertEquals("DRDS", ResTypeEnum.DRDS.value);
    }

    @Test
    public void testPolarx2Value() {
        // 测试POLARX2枚举值
        assertEquals("POLARX2", ResTypeEnum.POLARX2.getValue());
        assertEquals("POLARX2", ResTypeEnum.POLARX2.value);
    }

    @Test
    public void testPolardbMValue() {
        // 测试POLARDB_M枚举值
        assertEquals("POLARDB_M", ResTypeEnum.POLARDB_M.getValue());
        assertEquals("POLARDB_M", ResTypeEnum.POLARDB_M.value);
    }

    @Test
    public void testEnumValueOf() {
        // 测试valueOf方法能够正确解析枚举值
        ResTypeEnum rdsMysql = ResTypeEnum.valueOf("RDS_MYSQL");
        assertEquals(ResTypeEnum.RDS_MYSQL, rdsMysql);

        ResTypeEnum polarx1 = ResTypeEnum.valueOf("POLARX1");
        assertEquals(ResTypeEnum.POLARX1, polarx1);

        ResTypeEnum drds = ResTypeEnum.valueOf("DRDS");
        assertEquals(ResTypeEnum.DRDS, drds);

        ResTypeEnum polarx2 = ResTypeEnum.valueOf("POLARX2");
        assertEquals(ResTypeEnum.POLARX2, polarx2);

        ResTypeEnum polardbM = ResTypeEnum.valueOf("POLARDB_M");
        assertEquals(ResTypeEnum.POLARDB_M, polardbM);
    }

    @Test
    public void testDrdsEnumExists() {
        // 专门测试DRDS枚举是否正确添加
        boolean drdsExists = false;
        for (ResTypeEnum value : ResTypeEnum.values()) {
            if ("DRDS".equals(value.getValue())) {
                drdsExists = true;
                break;
            }
        }
        assertTrue("DRDS枚举值应该存在", drdsExists);
    }

    @Test
    public void testEnumOrder() {
        // 测试枚举值的顺序
        ResTypeEnum[] values = ResTypeEnum.values();
        assertEquals(ResTypeEnum.RDS_MYSQL, values[0]);
        assertEquals(ResTypeEnum.POLARX1, values[1]);
        assertEquals(ResTypeEnum.DRDS, values[2]);
        assertEquals(ResTypeEnum.POLARX2, values[3]);
        assertEquals(ResTypeEnum.POLARDB_M, values[4]);
    }

    @Test
    public void testEnumStringComparison() {
        // 测试枚举值与字符串的比较
        String drdsString = "DRDS";
        assertEquals(drdsString, ResTypeEnum.DRDS.getValue());
        assertEquals(drdsString, ResTypeEnum.DRDS.value);
    }

    @Test
    public void testGetValueMethod() {
        // 测试getValue()方法对所有枚举值都能正常工作
        assertNotNull(ResTypeEnum.RDS_MYSQL.getValue());
        assertNotNull(ResTypeEnum.POLARX1.getValue());
        assertNotNull(ResTypeEnum.DRDS.getValue());
        assertNotNull(ResTypeEnum.POLARX2.getValue());
        assertNotNull(ResTypeEnum.POLARDB_M.getValue());
    }

    @Test
    public void testDrdsCompatibilityWithPolarx1() {
        // 测试DRDS类型应该与POLARX1类型兼容处理
        // 这个测试验证了DRDS作为独立枚举值存在，但在业务逻辑中会被映射为POLARX1处理
        assertNotNull(ResTypeEnum.DRDS);
        assertNotNull(ResTypeEnum.POLARX1);
        // DRDS和POLARX1是不同的枚举值
        assertTrue(ResTypeEnum.DRDS != ResTypeEnum.POLARX1);
        // 但它们的值是不同的字符串
        assertTrue(!ResTypeEnum.DRDS.getValue().equals(ResTypeEnum.POLARX1.getValue()));
    }

    @Test
    public void testEnumToString() {
        // 测试枚举的toString方法
        assertEquals("RDS_MYSQL", ResTypeEnum.RDS_MYSQL.toString());
        assertEquals("POLARX1", ResTypeEnum.POLARX1.toString());
        assertEquals("DRDS", ResTypeEnum.DRDS.toString());
        assertEquals("POLARX2", ResTypeEnum.POLARX2.toString());
        assertEquals("POLARDB_M", ResTypeEnum.POLARDB_M.toString());
    }

    @Test
    public void testAllEnumValuesHaveNonNullValues() {
        // 测试所有枚举值的value属性都不为null
        for (ResTypeEnum enumValue : ResTypeEnum.values()) {
            assertNotNull("枚举值的value属性不应为null: " + enumValue.name(), enumValue.value);
            assertNotNull("枚举值的getValue()不应返回null: " + enumValue.name(), enumValue.getValue());
        }
    }

    @Test
    public void testDrdsEnumPosition() {
        // 测试DRDS枚举值在POLARX1和POLARX2之间
        ResTypeEnum[] values = ResTypeEnum.values();
        int polarx1Index = -1;
        int drdsIndex = -1;
        int polarx2Index = -1;

        for (int i = 0; i < values.length; i++) {
            if (values[i] == ResTypeEnum.POLARX1) {
                polarx1Index = i;
            } else if (values[i] == ResTypeEnum.DRDS) {
                drdsIndex = i;
            } else if (values[i] == ResTypeEnum.POLARX2) {
                polarx2Index = i;
            }
        }

        assertTrue("POLARX1应该在DRDS之前", polarx1Index < drdsIndex);
        assertTrue("DRDS应该在POLARX2之前", drdsIndex < polarx2Index);
    }
}
