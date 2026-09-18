/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

/**
 * CPU缓存行填充工具类，通过在字段前后添加64字节的填充数据，
 * 避免多线程场景下不同变量因共享同一缓存行而引起的伪共享(False Sharing)问题。
 * <p>
 * 结构：[64字节填充] [volatile字段] [64字节填充]
 * 分别提供long、Thread、boolean三种类型的填充包装。
 *
 * @author yaozhili
 */
public class Padding {
    /**
     * 基础填充类，提供64字节的前置填充，确保子类的volatile字段不与其他对象共享缓存行
     */
    private abstract static class Padding64 {
        private byte
            p10, p11, p12, p13, p14, p15, p16, p17,
            p20, p21, p22, p23, p24, p25, p26, p27,
            p30, p31, p32, p33, p34, p35, p36, p37,
            p40, p41, p42, p43, p44, p45, p46, p47,
            p50, p51, p52, p53, p54, p55, p56, p57,
            p60, p61, p62, p63, p64, p65, p66, p67,
            p70, p71, p72, p73, p74, p75, p76, p77;
    }

    private abstract static class Long0 extends Padding64 {
        public volatile long value;
    }

    /**
     * 带填充的long型变量，确保value字段独占缓存行
     */
    public static class PaddingLong extends Long0 {
        private byte
            p10, p11, p12, p13, p14, p15, p16, p17,
            p20, p21, p22, p23, p24, p25, p26, p27,
            p30, p31, p32, p33, p34, p35, p36, p37,
            p40, p41, p42, p43, p44, p45, p46, p47,
            p50, p51, p52, p53, p54, p55, p56, p57,
            p60, p61, p62, p63, p64, p65, p66, p67,
            p70, p71, p72, p73, p74, p75, p76, p77;

        public PaddingLong(long v) {
            this.value = v;
        }
    }

    private abstract static class Thread0 extends Padding64 {
        public volatile Thread value;
    }

    /**
     * 带填充的Thread引用变量，确保value字段独占缓存行
     */
    public static class PaddingThread extends Thread0 {
        private byte
            p10, p11, p12, p13, p14, p15, p16, p17,
            p20, p21, p22, p23, p24, p25, p26, p27,
            p30, p31, p32, p33, p34, p35, p36, p37,
            p40, p41, p42, p43, p44, p45, p46, p47,
            p50, p51, p52, p53, p54, p55, p56, p57,
            p60, p61, p62, p63, p64, p65, p66, p67,
            p70, p71, p72, p73, p74, p75, p76, p77;

        public PaddingThread(Thread v) {
            this.value = v;
        }
    }

    private abstract static class Boolean0 extends Padding64 {
        public volatile boolean value;
    }

    /**
     * 带填充的boolean变量，确保value字段独占缓存行
     */
    public static class PaddingBoolean extends Boolean0 {
        private byte
            p10, p11, p12, p13, p14, p15, p16, p17,
            p20, p21, p22, p23, p24, p25, p26, p27,
            p30, p31, p32, p33, p34, p35, p36, p37,
            p40, p41, p42, p43, p44, p45, p46, p47,
            p50, p51, p52, p53, p54, p55, p56, p57,
            p60, p61, p62, p63, p64, p65, p66, p67,
            p70, p71, p72, p73, p74, p75, p76, p77;

        public PaddingBoolean(boolean v) {
            this.value = v;
        }
    }
}
