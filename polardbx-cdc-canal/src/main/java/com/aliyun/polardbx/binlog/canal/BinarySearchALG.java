/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinarySearchALG {
    private int l;
    private int r;
    private final int originalLeft;
    private int direction = 1;
    private final long targetTso;

    public BinarySearchALG(int size, long targetTso) {
        this.targetTso = targetTso;
        this.originalLeft = this.l = 0;
        this.r = size - 1;
    }

    public int search(TsoSearcher searcher)throws Exception {
        while (l <= r) {
            int m = (l + r) / 2;
            Region region = searcher.search(m);
            log.info("search m={} in [{}, {}], region={}", m, l, r, region);
            if (region.result != null && !region.hasLossStartEvent){
                return m;
            }
            final int sourceM = m;
            while (!region.valid() || region.hasLossStartEvent){
                // 有xid，说明跨文件了，向前搜索start
                if (region.hasLossStartEvent){
                    direction = -1;
                    // 找到位点了，需要向前搜索，处理跨文件的问题。
                    log.info("already find pos, but has loss start xa transaction need process, reset left from {} to {}", l, originalLeft);
                    l = originalLeft;
                }
                if (direction > 0){
                    if (m + direction <= r){
                        m += direction;
                        log.info("go right and search m={} , region={}", m, region);
                    }else {
                        direction = -1;
                        m = sourceM;
                        log.info("go right and reach r limit  {}, will go left start with {}, region={}", l, m, region);
                    }
                }else {
                    if (m + direction >= l){
                        m += direction;
                        log.info("go left and search m={} , region={}", m, region);
                    }else {
                        log.warn("can not find target tso , because can not find tso in binlog files!");
                        return -1;
                    }
                }
                region = searcher.search(m);
                if (region.result != null && !region.hasLossStartEvent){
                    return m;
                }
            }

            if (region.maxTso < targetTso) {
                // 需要过滤掉sourceM与m之间的无效值
                l = Math.max(m, sourceM) + 1;
            } else if (region.minTso > targetTso){
                // 需要过滤掉sourceM与m之间的无效值
                r = Math.min(m, sourceM) - 1;
            } else {
                return m;
            }
        }
        return -1;
    }


    public interface TsoSearcher{
        Region search(int m) throws Exception;
    }

    public static class Region {
        public final long minTso;
        public final long maxTso;
        public final BinlogPosition result;
        public final boolean hasLossStartEvent;

        public Region(long minTso, long maxTso, BinlogPosition result, boolean hasLossStartEvent) {
            this.minTso = minTso;
            this.maxTso = maxTso;
            this.result = result;
            this.hasLossStartEvent = hasLossStartEvent;
        }

        public boolean valid(){
            return maxTso != -1;
        }

        @Override
        public String toString() {
            return "Region{" +
                "minTso=" + minTso +
                ", maxTso=" + maxTso +
                ", result=" + result +
                ", hasLossStartEvent='" + hasLossStartEvent + '\'' +
                '}';
        }
    }
}
