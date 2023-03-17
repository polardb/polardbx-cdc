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
package com.aliyun.polardbx.cdc.qatest.binlog.metadata;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 */
@Slf4j
public class TableMocker {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(List.class, new TypeAdapter<Object>() {
        @Override
        public void write(JsonWriter out, Object value) {

        }

        @Override
        public Object read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            switch (token) {
            case BEGIN_ARRAY:
                List<Object> list = new ArrayList();
                in.beginArray();
                while (in.hasNext()) {
                    list.add(read(in));
                }
                in.endArray();
                return list;
            case NUMBER:
                String n = in.nextString();
                if (n.indexOf('.') != -1) {
                    return Double.parseDouble(n);
                }
                return Long.parseLong(n);
            default:
                return in.nextString();
            }
        }
    }).create();

    private final String dbName;
    private final TableDetail tableDetail;
    private JdbcTemplate polardbxJdbcTemplate;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean created = new AtomicBoolean(false);

    @SneakyThrows
    public TableMocker(String dbName, String tableName) {
        this.dbName = dbName;
        this.tableDetail = init(tableName);
    }

    public void drop() {
        polardbxJdbcTemplate.execute("drop table IF EXISTS `" + dbName + "`.`" + tableDetail.tableName + "`");
    }

    public void create() {
        if (!created.getAndSet(true)) {
            log.info("create table:{}  \n{}", tableDetail.tableName, tableDetail.createSql);
            polardbxJdbcTemplate.execute(tableDetail.createSql);
        }
    }

    public void insert() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder placeholder = new StringBuilder("?");
        final List<Object> params = tableDetail.columnDetailList.stream().filter(c -> !c.isDropped()).map(
            columnDetail -> {
                int rand = random.nextInt(columnDetail.getDefaultValues().size());
                Object result = columnDetail.getDefaultValues().get(rand);
                placeholder.append(",");
                switch (columnDetail.columnType) {
                case "GEOMETRY":
                case "POINT":
                case "LINESTRING":
                case "POLYGON":
                case "MULTIPOINT":
                case "MULTILINESTRING":
                case "MULTIPOLYGON":
                case "GEOMETRYCOLLECTION":
                    placeholder.append("ST_GeomFromText(?)");
                    break;
                default:
                    placeholder.append("?");
                    break;
                }
                return result;
            }).collect(Collectors.toList());
        params.add(0, null);
        String sql = "insert into `" + dbName + "`.`" + tableDetail.tableName + "` values (" + placeholder + ")";
        polardbxJdbcTemplate.update(sql, params.toArray());
    }

    public void update() {
        final List<Integer> ids = polardbxJdbcTemplate.query(
            "select id from `" + dbName + "`.`" + tableDetail.getTableName() + "`",
            (i, j) -> i.getInt("id"));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Integer id : ids) {
            StringBuilder placeholder = new StringBuilder("SET id = ?");
            final List<Object> params = tableDetail.columnDetailList.stream().filter(c -> !c.isDropped()).map(
                columnDetail -> {
                    int rand = random.nextInt(columnDetail.getDefaultValues().size());
                    Object result = columnDetail.getDefaultValues().get(rand);
                    placeholder.append(",");
                    switch (columnDetail.columnType) {
                    case "GEOMETRY":
                    case "POINT":
                    case "LINESTRING":
                    case "POLYGON":
                    case "MULTIPOINT":
                    case "MULTILINESTRING":
                    case "MULTIPOLYGON":
                    case "GEOMETRYCOLLECTION":
                        placeholder.append(columnDetail.columnName + " = ST_GeomFromText(?)");
                        break;
                    default:
                        placeholder.append(columnDetail.columnName + " = ?");
                        break;
                    }
                    return result;
                }).collect(Collectors.toList());
            placeholder.append(" WHERE id = ?");
            params.add(0, id);
            params.add(id);
            log.info("placeHolder {}, params {}", placeholder, params);
            polardbxJdbcTemplate.update(
                "update `" + dbName + "`.`" + tableDetail.tableName + "` " + placeholder,
                params.toArray());
        }
    }

    public void addColumn() {
        final Optional<ColumnDetail> any = tableDetail.columnDetailList.stream().filter(
            columnDetail -> columnDetail.dropped == true).findAny();
        final List<String> columns = tableDetail.columnDetailList.stream().map(ColumnDetail::getColumnName).collect(
            Collectors.toList());

        any.ifPresent(c -> {
            c.setDropped(false);
            int index = columns.indexOf(c.columnName);
            final String sql = "alter table `" + dbName + "`.`" + tableDetail.tableName + "` add " + c.columnName + " "
                + c.columnType + " "
                + (
                index == 0 ? "AFTER ID" : "AFTER " + columns.get(index + 1));
            log.info("addColumn {}", sql);
            polardbxJdbcTemplate.update(sql);
        });
    }

    public void dropColumn() {
        final Optional<ColumnDetail> any = tableDetail.columnDetailList.stream().filter(
            columnDetail -> columnDetail.dropped == false).findAny();
        any.ifPresent(c -> {
            c.setDropped(true);
            final String sql =
                "alter table `" + dbName + "`.`" + tableDetail.tableName + "` drop column " + c.columnName;
            log.info("dropColumn {}", sql);
            polardbxJdbcTemplate.update(sql);
        });

    }

    public TableDetail init(String tableName) throws IOException {
        if (initialized.compareAndSet(false, true)) {

            final List<String> lines = FileUtils.readLines(
                new File(Resources.getResource("metadata/" + tableName + ".properties").getFile()),
                Charset.defaultCharset());
            List<ColumnDetail> columnDetailList = Lists.newArrayList();
            StringBuilder builder = new StringBuilder();
            builder.append("CREATE TABLE IF NOT EXISTS `" + dbName + "`.`").append(tableName.toLowerCase())
                .append("`(")
                .append(
                    System.getProperty("line.separator"));

            builder.append("\t").append("id").append(StringUtils.SPACE).append(
                "int unsigned NOT NULL AUTO_INCREMENT")
                .append(",").append(
                System.getProperty("line.separator"));

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("#")) {
                    continue;
                }
                final String[] typeValue = line.split("=");
                final String type = typeValue[0];
                final String value = typeValue[1];
                String name = "_" + type.replace(" ", "_") + "_";

                columnDetailList.add(ColumnDetail.builder().columnName(name).columnType(type)
                    .defaultValues(GSON.fromJson(value, List.class)).build());
                String fixedType;
                switch (type) {
                case "VARCHAR":
                    fixedType = "VARCHAR(16)";
                    break;
                case "VARBINARY":
                    fixedType = "VARBINARY(16)";
                    break;
                case "ENUM":
                    fixedType = "ENUM('S', 'M', 'L')";
                    break;
                case "SET":
                    fixedType = "SET('a','b','c','d')";
                    break;
                default:
                    fixedType = type;
                }

                builder.append("\t").append(name).append(StringUtils.SPACE).append(fixedType).append(",").append(
                    System.getProperty("line.separator"));

            }
            builder.append("\t").append("PRIMARY KEY (`id`)");
            //2do 添加分库分表规则
            builder.append(System.getProperty("line.separator")).append(
                ") dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2");
            return TableDetail.builder().tableName(tableName.toLowerCase()).createSql(builder.toString())
                .columnDetailList(
                    columnDetailList).build();
        } else {
            return this.tableDetail;
        }
    }

    public void setPolardbxJdbcTemplate(JdbcTemplate polardbxJdbcTemplate) {
        this.polardbxJdbcTemplate = polardbxJdbcTemplate;
    }

    public TableDetail getTableDetail() {
        return tableDetail;
    }

    @Override
    public String toString() {
        return tableDetail.tableName;
    }
}
