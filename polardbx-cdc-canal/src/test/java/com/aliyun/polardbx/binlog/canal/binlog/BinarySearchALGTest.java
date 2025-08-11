/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.BinarySearchALG;
import org.junit.Assert;
import org.junit.Test;

public class BinarySearchALGTest {
    @Test
    public void test1() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 58;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> new BinarySearchALG.Region(data[m], data[m], null, false));
        Assert.assertNotEquals(-1, index);
        Assert.assertEquals(target, data[index]);
    }

    @Test
    public void test2() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 1;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> new BinarySearchALG.Region(data[m], data[m], null, false));
        Assert.assertNotEquals(-1, index);
        Assert.assertEquals(target, data[index]);
    }

    @Test
    public void test3() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 59;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> new BinarySearchALG.Region(data[m], data[m], null, false));
        Assert.assertNotEquals(-1, index);
        Assert.assertEquals(target, data[index]);
    }

    @Test
    public void testWithInValid() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 59;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> {
            if (m < 55 && m > 2){
                return new BinarySearchALG.Region(-1, -1, null, false);
            }
            return new BinarySearchALG.Region(data[m], data[m], null, false);
        });
        Assert.assertNotEquals(-1, index);
        Assert.assertEquals(target, data[index]);
    }

    @Test
    public void testWithInValid2() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 1;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> {
            if (m < 55 && m > 2){
                return new BinarySearchALG.Region(-1, -1, null, false);
            }
            return new BinarySearchALG.Region(data[m], data[m], null, false);
        });
        Assert.assertNotEquals(-1, index);
        Assert.assertEquals(target, data[index]);
    }

    @Test
    public void testWithInValid3() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 100;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> {
            if (m < 55 && m > 2){
                return new BinarySearchALG.Region(-1, -1, null, false);
            }
            return new BinarySearchALG.Region(data[m], data[m], null, false);
        });
        Assert.assertEquals(-1, index);
    }

    @Test
    public void testWithCanNotFind() throws Exception {
        long [] data = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        long target = 100;
        BinarySearchALG binarySearchALG = new BinarySearchALG(data.length, target);
        int index = binarySearchALG.search(m -> new BinarySearchALG.Region(data[m], data[m], null, false));
        Assert.assertEquals(-1, index);
    }
}
