/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.TaskInfoProvider;

/**
 * Unit test for simple App.
 */
public class ClusterDumperBootStrapTest_1 {

    //sh stress.sh Dumper-1 "taskName=Dumper-1 dumper.assignedTargetFinalTaskAddress=127.0.0.1:9999 binlog.write.dryrun=true"
    public static void main(String[] args) {
        DumperBootStrap bootStrap = new DumperBootStrap();
        bootStrap.setTaskInfoProvider(new TaskInfoProvider("Dumper-1"));
        String username = System.getenv("USER");
        bootStrap
            .boot(new String[] {
                String.format("taskName=Dumper-1 dumper.assignedTargetFinalTaskAddress=127.0.0.1:9999 "
                        + "binlog.dir.path=/Users/%s/Documents/polardbx-binlog/dumper-1/binlog/ "
                        + "binlog.file.size=1048576000 " + "dumper.cluster.id=dumper-test-%s "
                        + "binlog.write.flush.policy=0 " + "binlog.write.buffer.size=1048576 "
                        + "binlog.write.dryrun=true",
                    username,
                    username)});
    }
}
