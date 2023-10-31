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
package com.aliyun.polardbx.rpl.extractor.search.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.rpl.extractor.search.SearchContext;
import org.apache.commons.lang3.StringUtils;

/**
 * 根据TSO搜索置定位点，当前算法只搜索CTS
 */
public class TsoSearchHandler implements ISearchHandler {
    private final String tso;

    public TsoSearchHandler(String tso) {
        this.tso = tso;
    }

    @Override
    public boolean isEnd(LogEvent event, SearchContext context) {
        String currentTSO = context.getCurrentTSO();
        if (StringUtils.isNotBlank(currentTSO) && StringUtils.compare(currentTSO, tso) >= 0) {
            return true;
        }

        return false;
    }

    @Override
    public boolean accept(LogEvent event, SearchContext context) {
        return (event instanceof RowsQueryLogEvent) && StringUtils.isNotBlank(context.getCurrentTSO());
    }

    @Override
    public void handle(LogEvent event, SearchContext context) {
        BinlogPosition position =
            new BinlogPosition(context.getCurrentSearchFile(), event.getLogPos(), event.getServerId(), event.getWhen());
        position.setRtso(context.getCurrentTSO());
        context.setResultPosition(position);
    }
}
