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
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.rpl.extractor.search.SearchContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据指定pos搜索消费起始位点,如果是polarx，则会同时设置对应的tso
 */
public class PositionSearchHandler implements ISearchHandler {

    private static final Logger logger = LoggerFactory.getLogger(PositionSearchHandler.class);

    private final BinlogPosition entryPosition;

    public PositionSearchHandler(BinlogPosition entryPosition) {
        this.entryPosition = entryPosition;
    }

    @Override
    public boolean isEnd(LogEvent event, SearchContext context) {
        long curPos = event.getLogPos();

        if (curPos == entryPosition.getPosition()) {
            if (accept(event, context)) {
                logger.info("start entry pos was accept and set return");
                context.setResultPosition(entryPosition);
            } else {
                logger.info("entry pos was not accept and try set to last pos " + context.getLastPosition()
                    + " event type : " + event.getHeader().getType());
                context.setResultPosition(context.getLastPosition());
            }
            context.setLockPosition(true);
        }
        if (curPos > entryPosition.getPosition()) {

            if (context.getResultPosition() == null && context.getProcessEventCount() == 0) {
                logger.info(
                    "search entry pos not found when first event is not available , was reset to start entry pos ");

                // 比endPosition小的begin/end或者ddl都是满足的位点，如果没找到search pos ，说明没有搜索到 transaction和ddl，
                //说明pos前都是cts，可以作为位点直接返回
                context.setResultPosition(entryPosition);
                context.setLockPosition(true);
            }

            BinlogPosition resultPosition = context.getResultPosition();

            if (resultPosition != null && StringUtils.isBlank(resultPosition.getRtso())) {
                // 有位点，但是没有tso，可以在继续搜索
                resultPosition.setRtso(context.getCurrentTSO());
            }
            if (context.getResultPosition() != null && StringUtils.isBlank(resultPosition.getRtso())) {
                // 经过一些列操作，还是没有找到tso
                return false;
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean accept(LogEvent event, SearchContext context) {
        if (event instanceof QueryLogEvent) {
            // ddl or begin
            return true;
        }

        if (event instanceof XidLogEvent) {
            return true;
        }

        if (StringUtils.isNotBlank(context.getCurrentTSO())) {
            BinlogPosition lastPos = context.getLastPosition();
            if (lastPos != null && StringUtils.isBlank(lastPos.getRtso())) {
                // lastPos 没有rtso，只能是begin 或者end,则直接取后面的cts设置
                lastPos.setRtso(context.getCurrentTSO());
            }
        }

        return false;
    }

    @Override
    public void handle(LogEvent event, SearchContext context) {
        BinlogPosition resultPosition;
        if (context.isLockPosition()) {
            resultPosition = context.getResultPosition();
        } else {
            resultPosition =
                new BinlogPosition(context.getCurrentSearchFile(), event.getLogPos(), event.getServerId(),
                    event.getWhen());
            context.setLastPosition(resultPosition);
        }
        if (StringUtils.isNotBlank(context.getCurrentTSO())) {
            resultPosition.setRtso(context.getCurrentTSO());
        }

        context.setProcessEventCount(context.getProcessEventCount() + 1);
    }
}
