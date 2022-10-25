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
