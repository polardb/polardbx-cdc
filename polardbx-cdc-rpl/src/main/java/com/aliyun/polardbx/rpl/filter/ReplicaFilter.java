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
package com.aliyun.polardbx.rpl.filter;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author shicai.xsc 2021/3/1 13:28
 * @since 5.0.0.0
 */
@Slf4j
public class ReplicaFilter extends BaseFilter {

    private ReplicaMeta replicaMeta;
    private Set<String> ignoreTables;
    private Set<String> doTables;
    private Set<String> ignoreDbs;
    private Set<String> doDbs;
    private Set<Long> ignoreServerIds;
    private List<List<Pattern>> wildIgnoreTables;
    private List<List<Pattern>> wildDoTables;
    private Map<String, Boolean> filterCache;
    private Map<String, String> rewriteDbs;
    private String skipTso;
    private String skipUntilTso;
    private boolean needCheckSkip;

    public ReplicaFilter(ReplicaMeta replicaMeta) {
        this.replicaMeta = replicaMeta;
    }

    @Override
    public void init() {
        doTables = initFilterSet(replicaMeta.getDoTable());
        ignoreTables = initFilterSet(replicaMeta.getIgnoreTable());
        doDbs = initFilterSet(replicaMeta.getDoDb());
        ignoreDbs = initFilterSet(replicaMeta.getIgnoreDb());
        ignoreServerIds = initIgnoreServerIds(replicaMeta.getIgnoreServerIds());
        wildDoTables = initWildPatternPairs(replicaMeta.getWildDoTable());
        wildIgnoreTables = initWildPatternPairs(replicaMeta.getWildIgnoreTable());
        filterCache = new HashMap<>(128);
        rewriteDbs = initRewriteDbs(replicaMeta.getRewriteDb());
        skipTso = replicaMeta.getSkipTso();
        skipUntilTso = replicaMeta.getSkipUntilTso();
        if (StringUtils.isNotEmpty(skipTso) || StringUtils.isNotEmpty(skipUntilTso)) {
            needCheckSkip = true;
        }
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/sql_parse.cc bool
     * mysql_test_parse_for_slave(THD *thd) bool all_tables_not_ok(THD *thd,
     * TABLE_LIST *tables)
     */
    public boolean ignoreEvent(DefaultRowChange rowChange) {
        return ignoreEvent(getRewriteDb(rowChange.getSchema(), DBMSAction.INSERT),
            rowChange.getTable(), rowChange.getAction(), Integer.MIN_VALUE);
    }

    @Override
    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        if (ignoreServerIds.contains(serverId)) {
            return true;
        }

        String key = schema + "." + tbName + "." + action.name();
        if (filterCache.containsKey(key)) {
            return filterCache.get(key);
        }

        boolean result = !tableOk(schema, tbName) || !dbOk(schema, action);
        filterCache.put(key, result);

        if (result) {
            StatMetrics.getInstance().addSkipCount(1);
        }
        return result;
    }

    @Override
    public boolean ignoreEventByTso(String tso) {
        if (!needCheckSkip || StringUtils.isEmpty(tso)) {
            return false;
        }
        boolean result = false;
        if (StringUtils.isNotEmpty(skipTso)) {
            if (StringUtils.equals(tso, skipTso)) {
                result = true;
                skipTso = null;
            }
        }
        if (StringUtils.isNotEmpty(skipUntilTso)) {
            if (StringUtils.compare(tso, skipUntilTso) < 0) {
                result = true;
            } else {
                skipUntilTso = null;
            }
        }
        if (skipTso == null && skipUntilTso == null) {
            needCheckSkip = false;
        }
        if (result) {
            StatMetrics.getInstance().addSkipCount(1);
        }

        return result;
    }

    @Override
    public String getRewriteDb(String schema, DBMSAction action) {
        if (action != DBMSAction.CREATEDB && action != DBMSAction.DROPDB) {
            return rewriteDbs.getOrDefault(schema, schema);
        }
        return schema;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::tables_ok(const char *db, TABLE_LIST *tables)
     */
    private boolean tableOk(String db, String tb) {
        String fullTbName = db + "." + tb;
        if (doTables.size() > 0 && doTables.contains(fullTbName)) {
            return true;
        }

        if (ignoreTables.size() > 0 && ignoreTables.contains(fullTbName)) {
            return false;
        }

        if (wildDoTables.size() > 0 && findWildTable(wildDoTables, db, tb)) {
            return true;
        }

        if (wildIgnoreTables.size() > 0 && findWildTable(wildIgnoreTables, db, tb)) {
            return false;
        }

        return doTables.size() == 0 && wildDoTables.size() == 0;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/sql_parse.cc inline
     * bool check_database_filters(THD *thd, const char *db, enum_sql_command
     * sql_cmd)
     */
    private boolean dbOk(String db, DBMSAction action) {
        boolean dbOk = dbOk(db);

        if (dbOk && doDbs.size() == 0 && ignoreDbs.size() == 0) {
            switch (action) {
            case CREATEDB:
            case DROPDB:
                // no ALTERDB in Canal
                // case ALTERDB:
                dbOk = dbOkWithWildTable(db);
                break;
            }
        }

        return dbOk;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::db_ok(const char *db, bool need_increase_counter)
     */
    private boolean dbOk(String db) {
        if (doDbs.size() > 0) {
            return doDbs.contains(db);
        }

        if (ignoreDbs.size() > 0) {
            return !ignoreDbs.contains(db);
        }

        return true;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::db_ok_with_wild_table(const char *db)
     */
    private boolean dbOkWithWildTable(String db) {
        if (wildDoTables.size() > 0 && findWildTable(wildDoTables, db, "")) {
            return true;
        }

        if (wildIgnoreTables.size() > 0 && findWildTable(wildIgnoreTables, db, "")) {
            return false;
        }

        return wildDoTables.size() == 0;
    }

    private boolean findWildDb(List<List<Pattern>> wilds, String db) {
        for (List<Pattern> patterns : wilds) {
            if (patterns.get(0).matcher(db).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean findWildTable(List<List<Pattern>> wilds, String db, String tb) {
        for (List<Pattern> patterns : wilds) {
            if (patterns.get(0).matcher(db).matches() && patterns.get(1).matcher(tb).matches()) {
                return true;
            }
        }
        return false;
    }

    private List<List<Pattern>> initWildPatternPairs(String fullWildPairStr) {
        List<List<Pattern>> wildPatternPairs = new ArrayList<>();
        if (StringUtils.isBlank(fullWildPairStr)) {
            return wildPatternPairs;
        }

        for (String token : fullWildPairStr.trim().toLowerCase().split(RplConstants.COMMA)) {
            wildPatternPairs.add(initWildPattern(token));
        }

        return wildPatternPairs;
    }

    private Map<String, String> initRewriteDbs(String rewriteDbStr) {
        Map<String, String> rewriteDbs = new HashMap<>();
        if (StringUtils.isBlank(rewriteDbStr)) {
            return rewriteDbs;
        }

        String[] rewriteDbList = rewriteDbStr.split("\\)");
        for (String rewriteDb : rewriteDbList) {
            rewriteDb = CommonUtil.trimLeftAll(rewriteDb.trim(), ',');
            rewriteDb = CommonUtil.removeBracket(rewriteDb.trim());
            String[] tokens = rewriteDb.split(",");
            rewriteDbs.put(tokens[0].trim(), tokens[1].trim());
        }
        return rewriteDbs;
    }

    private List<Pattern> initWildPattern(String wildPairStr) {
        String[] wildStrs = wildPairStr.split("\\.");
        Pattern dbPattern = getWildPattern(wildStrs[0]);
        Pattern tbPattern = getWildPattern(wildStrs[1]);
        List<Pattern> patterns = new ArrayList<>();
        patterns.add(dbPattern);
        patterns.add(tbPattern);
        return patterns;
    }

    private Pattern getWildPattern(String wildStr) {
        String filter = wildStr.trim().replace("\\_", "(").replace("\\%", ")");
        filter = filter.replace("_", ".").replace("%", ".*");
        filter = filter.replace("(", "_").replace(")", "%");
        return Pattern.compile(filter);
    }
}
