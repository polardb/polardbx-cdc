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
package com.aliyun.polardbx.binlog.proc;

import lombok.extern.slf4j.Slf4j;
import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.ProcFd;
import org.hyperic.sigar.ProcMem;
import org.hyperic.sigar.ProcTime;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

import static com.aliyun.polardbx.binlog.ConfigKeys.SIGAR_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class ProcUtils {

    public static ProcSnapshot buildProcSnapshot() {
        if (getBoolean(SIGAR_ENABLED) && !"unknown".equalsIgnoreCase(Sigar.NATIVE_VERSION_STRING)) {
            try {
                ProcSnapshot procSnapshot = new ProcSnapshot();

                long pid = Holder.SIGAR.getPid();
                ProcCpu procCpu = Holder.SIGAR.getProcCpu(pid);
                ProcMem procMem = Holder.SIGAR.getProcMem(pid);
                ProcTime procTime = Holder.SIGAR.getProcTime(pid);
                ProcFd procFd = Holder.SIGAR.getProcFd(pid);

                //build result
                procSnapshot.setPid(pid);
                procSnapshot.setCpuUser(procCpu.getUser());
                procSnapshot.setCpuSys(procCpu.getSys());
                procSnapshot.setCpuTotal(procCpu.getTotal());
                procSnapshot.setCpuPercent(procCpu.getPercent());
                procSnapshot.setMemSize(procMem.getSize());
                procSnapshot.setStartTime(procTime.getStartTime());
                procSnapshot.setFdNum(procFd.getTotal());
                return procSnapshot;
            } catch (SigarException e) {
                log.error("build os snapshot error ", e);
                return null;
            }
        }
        return null;
    }

    private static class Holder {
        private static final Sigar SIGAR = new Sigar();
    }
}
