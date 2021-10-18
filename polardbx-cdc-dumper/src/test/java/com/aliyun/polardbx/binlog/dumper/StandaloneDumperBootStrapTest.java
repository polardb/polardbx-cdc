//package com.aliyun.polardbx.binlog.dumper;
//
//import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
//
///**
// * Unit test for simple App.
// */
//public class StandaloneDumperBootStrapTest {
//
//    public static void main(String[] args) {
//        DumperBootStrap bootStrap = new DumperBootStrap();
//        bootStrap.setDumperStatusMonitor(new DumperStatusMonitor() {
//
//            @Override
//            public void start() {
//
//            }
//
//            @Override
//            public void stop() {
//
//            }
//
//            @Override
//            public void addListener(DumperStatusListener listener) {
//
//            }
//
//            @Override
//            public DumperInfo getLeader() {
//                return null;
//            }
//
//            @Override
//            public synchronized boolean isLeader() {
//                return true;
//            }
//        });
//        String username = System.getenv("USER");
//        System.setProperty("targetFinalTask", "127.0.0.1:8912");
//        bootStrap.boot(new String[] { String.format(
//            "binlog.dir.path=/Users/%s/Documents/polardbx-binlog/dumper/binlog/ " + "binlog.file.size=1048576",
//            username) });
//    }
//}
