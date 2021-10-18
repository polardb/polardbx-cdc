//package com.aliyun.polardbx.binlog.cdc;
//
//import java.util.Properties;
//
//import com.facebook.presto.sql.parser.ParsingOptions;
//import com.facebook.presto.sql.tree.Statement;
//import org.apache.calcite.config.Lex;
//import org.apache.calcite.sql.SqlNode;
//import org.apache.calcite.sql.dialect.OracleSqlDialect;
//import org.apache.calcite.sql.parser.SqlParseException;
//import org.apache.calcite.sql.parser.SqlParser;
//import org.apache.calcite.sql.parser.SqlParserUtil;
//import org.apache.calcite.sql.validate.SqlConformanceEnum;
//import org.apache.shardingsphere.sql.parser.SQLParserEngine;
//import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
//import org.junit.Test;
//
///**
// * @Author ShuGuang
// * @Description
// * @Date 2020/12/30 5:32 下午
// */
//public class TestParse {
//    @Test
//    public void test1() {
//        String sql = "select age as b, name n from table1 join table2 where id=1 and name='lu';";
//        Properties props = new Properties();
//        props.setProperty("parameterized", "false");
//        SQLParserEngine parserEngine = new SQLParserEngine("MySQL");
//        final SQLStatement parse = parserEngine.parse(sql, false);
//
//        System.out.println(parse);
//    }
//
//    @Test
//    public void test2() throws SqlParseException {
//        String sql = "select * from (select * from a order by id limit 10,1) b";
//        //sql = "select * from a";
//        // 解析配置
//        SqlParser.Config mysqlConfig = SqlParser.config().withLex(Lex.MYSQL).withConformance(
//            SqlConformanceEnum.MYSQL_5);
//        // 创建解析器
//        SqlParser parser = SqlParser.create(sql, mysqlConfig);
//        // 解析sql
//        SqlNode sqlNode = parser.parseQuery();
//        System.out.println(sqlNode.getKind());
//        System.out.println("=========================");
//        // 还原某个方言的SQL
//        System.out.println(sqlNode.toSqlString(OracleSqlDialect.DEFAULT));
//
//        SqlParserUtil.parseCollation("");
//    }
//
//    @Test
//    public void test3() throws SqlParseException {
//        com.facebook.presto.sql.parser.SqlParser sqlParser = new com.facebook.presto.sql.parser.SqlParser();
//
//        final Statement statement = sqlParser.createStatement(
//            "select * from a order by id limit ALL", ParsingOptions.builder().build());
//        System.out.println(statement);
//    }
//}
