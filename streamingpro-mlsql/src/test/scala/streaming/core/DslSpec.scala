package streaming.core

import java.io.File

import net.sf.json.JSONObject
import org.apache.commons.io.FileUtils
import org.apache.spark.streaming.BasicSparkOperation
import streaming.core.strategy.platform.SparkRuntime
import streaming.dsl.{GrammarProcessListener, MLSQLExecuteContext, ScriptSQLExec, ScriptSQLExecListener}
import streaming.dsl.template.TemplateMerge

/**
  * Created by allwefantasy on 26/4/2018.
  */
class DslSpec extends BasicSparkOperation with SpecFunctions with BasicMLSQLConfig {


  "set grammar" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      var sq = createSSEL
      ScriptSQLExec.parse(s""" set hive.exec.dynamic.partition.mode=nonstric options type = "conf" and jack = "" ; """, sq)
      assert(sq.env().contains("hive.exec.dynamic.partition.mode"))

      sq = createSSEL
      ScriptSQLExec.parse(s""" set  xx = `select unix_timestamp()` options type = "sql" ; """, sq)
      assert(sq.env()("xx").toInt > 0)

      sq = createSSEL
      ScriptSQLExec.parse(s""" set  xx = "select unix_timestamp()"; """, sq)
      assert(sq.env()("xx") == "select unix_timestamp()")

    }
  }

  "set grammar case 2" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      implicit val spark = runtime.sparkSession

      var sq = createSSEL
      ScriptSQLExec.parse(""" set a = "valuea"; set b = "${a}/b"; """, sq)
      assert(sq.env()("b") == "valuea/b")

      sq = createSSEL
      ScriptSQLExec.parse(""" set b = "${a}/b"; set a = "valuea";  """, sq)
      assert(sq.env()("b") == "valuea/b")

    }
  }



  "save mysql with update" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      //注册表连接
      var sq = createSSEL
      ScriptSQLExec.parse("connect jdbc where driver=\"com.mysql.jdbc.Driver\"\nand url=\"jdbc:mysql://127.0.0.1:3306/wow?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false\"\nand driver=\"com.mysql.jdbc.Driver\"\nand user=\"root\"\nand password=\"csdn.net\"\nas tableau;", sq)

      sq = createSSEL
      ScriptSQLExec.parse("select \"a\" as a,\"b\" as b\n,\"c\" as c\nas tod_boss_dashboard_sheet_1;", sq)

      jdbc("drop table tod_boss_dashboard_sheet_1")

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save append tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_1`
           |options truncate="true"
           |and idCol="a,b"
           |and createTableColumnTypes="a VARCHAR(128),b VARCHAR(128)";
           |load jdbc.`tableau.tod_boss_dashboard_sheet_1` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 1)

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save append tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_1`
           |options idCol="a,b";
           |load jdbc.`tableau.tod_boss_dashboard_sheet_1` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 1)

      sq = createSSEL
      ScriptSQLExec.parse("select \"k\" as a,\"b\" as b\n,\"c\" as c\nas tod_boss_dashboard_sheet_1;", sq)

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save append tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_1`
           |;
           |load jdbc.`tableau.tod_boss_dashboard_sheet_1` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 2)
    }
  }

  "save mysql with default" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      //注册表连接
      var sq = createSSEL
      ScriptSQLExec.parse("connect jdbc where driver=\"com.mysql.jdbc.Driver\"\nand url=\"jdbc:mysql://127.0.0.1:3306/wow?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false\"\nand driver=\"com.mysql.jdbc.Driver\"\nand user=\"root\"\nand password=\"csdn.net\"\nas tableau;", sq)

      sq = createSSEL
      ScriptSQLExec.parse("select \"a\" as a,\"b\" as b\n,\"c\" as c\nas tod_boss_dashboard_sheet_1;", sq)

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save overwrite tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_2`
           |options truncate="false";
           |load jdbc.`tableau.tod_boss_dashboard_sheet_2` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 1)

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save append tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_2`
           |;
           |load jdbc.`tableau.tod_boss_dashboard_sheet_2` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 2)

      sq = createSSEL
      ScriptSQLExec.parse(
        s"""
           |save overwrite tod_boss_dashboard_sheet_1
           |as jdbc.`tableau.tod_boss_dashboard_sheet_2` options truncate="true"
           |;
           |load jdbc.`tableau.tod_boss_dashboard_sheet_2` as tbs;
         """.stripMargin, sq)

      assume(spark.sql("select * from tbs").toJSON.collect().size == 1)

    }
  }

  //  "insert with variable" should "work fine" in {
  //
  //    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
  //      //执行sql
  //      implicit val spark = runtime.sparkSession
  //
  //      var sq = createSSEL
  //      sq = createSSEL
  //      ScriptSQLExec.parse("select \"a\" as a,\"b\" as b\n,\"c\" as c\nas tod_boss_dashboard_sheet_1;", sq)
  //
  //      sq = createSSEL
  //      ScriptSQLExec.parse("set hive.exec.dynamic.partition.mode=nonstric options type = \"conf\" ;" +
  //        "set HADOOP_DATE_YESTERDAY=`2017-01-02` ;" +
  //        "INSERT OVERWRITE TABLE default.abc partition (hp_stat_date = '${HADOOP_DATE_YESTERDAY}') " +
  //        "select * from tod_boss_dashboard_sheet_1;", sq)
  //
  //    }
  //  }

  def createFile(path: String, content: String) = {
    val f = new File("/tmp/abc.txt")
    if (!f.exists()) {
      FileUtils.write(f, "天了噜", "utf-8")
    }
  }

  "analysis with dic" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      //需要有一个/tmp/abc.txt 文件，里面包含"天了噜"

      val f = new File("/tmp/abc.txt")
      if (!f.exists()) {
        FileUtils.write(f, "天了噜", "utf-8")
      }

      var sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("token-analysis"), sq)
      val res = spark.sql("select * from tb").toJSON.collect().mkString("\n")
      println(res)
      import scala.collection.JavaConversions._
      assume(JSONObject.fromObject(res).getJSONArray("keywords").
        filter(f => f.asInstanceOf[String]
          == "天了噜/userDefine").size > 0)
    }
  }

  "analysis with dic and deduplicate" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      //需要有一个/tmp/abc.txt 文件，里面包含"天了噜"
      var sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("token-analysis-deduplicate"), sq)
      val res = spark.sql("select * from tb").toJSON.collect().mkString("\n")
      println(res)
      import scala.collection.JavaConversions._
      assume(JSONObject.fromObject(res).getJSONArray("keywords").
        filter(f => f.asInstanceOf[String]
          == "天了噜/userDefine").size == 1)
    }
  }

  "analysis with dic with n nature include" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      //需要有一个/tmp/abc.txt 文件，里面包含"天了噜"
      var sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("token-analysis-include-n"), sq)
      val res = spark.sql("select * from tb").toJSON.collect().mkString("\n")
      println(res)
      import scala.collection.JavaConversions._
      assume(JSONObject.fromObject(res).getJSONArray("keywords").size() == 1)
      assume(JSONObject.fromObject(res).getJSONArray("keywords").
        filter(f => f.asInstanceOf[String]
          == "天才/n").size > 0)
    }
  }

  "extract with dic" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      //需要有一个/tmp/abc.txt 文件，里面包含"天了噜"
      var sq = createSSEL
      sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("token-extract"), sq)
      val res = spark.sql("select * from tb").toJSON.collect().mkString("\n")
      println(res)
      import scala.collection.JavaConversions._
      assume(JSONObject.fromObject(res).getJSONArray("keywords").
        filter(f => f.asInstanceOf[String].
          startsWith("天了噜")).size > 0)
    }
  }

  "save with file num options" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("save-filenum"), sq)
      assume(new File("/tmp/william/tmp/abc/").list().filter(f => f.endsWith(".json")).size == 3)
    }
  }

  "carbondata save" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParamsWithCarbondata, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      var sq = createSSEL
      var tableName = "visit_carbon3"

      dropTables(Seq(tableName))

      ScriptSQLExec.parse(TemplateMerge.merge(loadSQLScriptStr("mlsql-carbondata"), Map("tableName" -> tableName)), sq)
      Thread.sleep(1000)
      var res = spark.sql("select * from " + tableName).toJSON.collect()
      var keyRes = JSONObject.fromObject(res(0)).getString("a")
      assume(keyRes == "1")

      dropTables(Seq(tableName))

      sq = createSSEL
      tableName = "visit_carbon4"

      dropTables(Seq(tableName))

      ScriptSQLExec.parse(TemplateMerge.merge(loadSQLScriptStr("mlsql-carbondata-without-option"), Map("tableName" -> tableName)), sq)
      Thread.sleep(1000)
      res = spark.sql("select * from " + tableName).toJSON.collect()
      keyRes = JSONObject.fromObject(res(0)).getString("a")
      assume(keyRes == "1")
      dropTables(Seq(tableName))

    }
  }

  "script-support-drop" should "work fine" taggedAs (NotToRunTag) in {

    withBatchContext(setupBatchContext(batchParamsWithCarbondata, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      val tableName = "visit_carbon5"

      var sq = createSSEL

      ScriptSQLExec.parse(TemplateMerge.merge(loadSQLScriptStr("mlsql-carbondata"), Map("tableName" -> tableName)), sq)
      Thread.sleep(1000)
      val res = spark.sql("select * from " + tableName).toJSON.collect()
      val keyRes = JSONObject.fromObject(res(0)).getString("a")
      assume(keyRes == "1")

      sq = createSSEL
      ScriptSQLExec.parse(TemplateMerge.merge(loadSQLScriptStr("script-support-drop"), Map("tableName" -> tableName)), sq)
      try {
        spark.sql("select * from " + tableName).toJSON.collect()
        assume(0 == 1)
      } catch {
        case e: Exception =>
      }

    }
  }


  "ScalaScriptUDF" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL

      ScriptSQLExec.parse(
        """
          |/*
          |  MLSQL脚本完成UDF注册示例
          |*/
          |
          |-- 填写script脚本
          |set plusFun='''
          |class PlusFun{
          |  def plusFun(a:Double,b:Double)={
          |   a + b
          |  }
          |}
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScalaScriptUDF.`scriptTable` as plusFun options
          |className="PlusFun"
          |and methodName="plusFun"
          |;
          |
          |-- 使用plusFun
          |select plusFun(1,1) as res as output;
        """.stripMargin, sq)
      var res = spark.sql("select * from output").collect().head.get(0)
      assume(res == 2)


      ScriptSQLExec.parse(
        """
          |/*
          |  MLSQL脚本完成UDF注册示例
          |*/
          |
          |-- 填写script脚本
          |set plusFun='''
          |def plusFun(a:Double,b:Double)={
          |   a + b
          |}
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScalaScriptUDF.`scriptTable` as plusFun options
          |and methodName="plusFun"
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(1,1) as res from dataTable as output;
        """.stripMargin, sq)
      res = spark.sql("select * from output").collect().head.get(0)
      assume(res == 2)
      res = spark.sql("select plusFun(1,1)").collect().head.get(0)
      assume(res == 2)

      ScriptSQLExec.parse(
        """
          |/*
          |  MLSQL脚本完成UDF注册示例
          |*/
          |
          |-- 填写script脚本
          |set plusFun='''
          |def apply(a:Double,b:Double)={
          |   a + b
          |}
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScalaScriptUDF.`scriptTable` as plusFun
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(1,1) as res from dataTable as output;
        """.stripMargin, sq)

      res = spark.sql("select plusFun(1,1)").collect().head.get(0)
      assume(res == 2)
    }
  }
  "pyton udf" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams ++ Array("-spark.sql.codegen.wholeStage", "false"), "classpath:///test/empty.json")) { runtime: SparkRuntime =>

      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(
        """
          |
          |-- 填写script脚本
          |set plusFun='''
          |def plusFun(self,a,b,c,d,e,f):
          |    return a+b+c+d+e+f
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |and methodName="plusFun"
          |and lang="python"
          |and dataType="integer"
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(1, 2, 3, 4, 5, 6) as res from dataTable as output;
        """.stripMargin, sq)
      var res = spark.sql("select * from output").collect().head.get(0)
      assume(res == 21)

      ScriptSQLExec.parse(
        """
          |
          |-- 填写script脚本
          |set plusFun='''
          |def plusFun(self,m):
          |    return "-".join(m)
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |and methodName="plusFun"
          |and lang="python"
          |and dataType="string"
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(array('a','b')) as res from dataTable as output;
        """.stripMargin, sq)
      res = spark.sql("select * from output").collect().head.get(0)
      assume(res == "a-b")
      ScriptSQLExec.parse(
        """
          |
          |-- 填写script脚本
          |set plusFun='''
          |def plusFun(self,m):
          |    return m
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |and methodName="plusFun"
          |and lang="python"
          |and dataType="map(string,string)"
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(map('a','b')) as res from dataTable as output;
        """.stripMargin, sq)
      res = spark.sql("select * from output").collect().head.get(0)
      assume(res.asInstanceOf[Map[String, String]]("a") == "b")

      ScriptSQLExec.parse(
        """
          |
          |-- 填写script脚本
          |set plusFun='''
          |def apply(self,m):
          |    return m
          |''';
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |and lang="python"
          |and dataType="map(string,string)"
          |;
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |-- 使用plusFun
          |select plusFun(map('a','b')) as res from dataTable as output;
        """.stripMargin, sq)
      res = spark.sql("select * from output").collect().head.get(0)
      assume(res.asInstanceOf[Map[String, String]]("a") == "b")

    }
  }



  "include" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      val source =
        """
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScalaScriptUDF.`scriptTable` as plusFun options
          |className="PlusFun"
          |and methodName="plusFun"
          |;
          |
          |-- 使用plusFun
          |select plusFun(1,1) as res as output;
        """.stripMargin
      writeStringToFile("/tmp/william/tmp/kk.jj", source)
      ScriptSQLExec.parse(
        """
          |-- 填写script脚本
          |set plusFun='''
          |class PlusFun{
          |  def plusFun(a:Double,b:Double)={
          |   a + b
          |  }
          |}
          |''';
          |include hdfs.`/tmp/kk.jj`;
        """.stripMargin
        , sq, false)
      val res = spark.sql("select * from output").collect().head.get(0)
      assume(res == 2)
    }
  }

  "include set" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      var sq = createSSEL
      var source =
        """
          |set a="b" options type="defaultParam";
          |
        """.stripMargin
      writeStringToFile("/tmp/william/tmp/kk.jj", source)
      ScriptSQLExec.parse(
        """
          |set a="c";
          |include hdfs.`/tmp/kk.jj`;
          |select "${a}" as res as display;
        """.stripMargin
        , sq, false)
      var res = spark.sql("select * from display").collect().head.get(0)
      assume(res == "c")

      sq = createSSEL
      source =
        """
          |set a="b";
          |
        """.stripMargin
      writeStringToFile("/tmp/william/tmp/kk.jj", source)
      ScriptSQLExec.parse(
        """
          |set a="c";
          |include hdfs.`/tmp/kk.jj`;
          |select "${a}" as res as display;
        """.stripMargin
        , sq, false)
      res = spark.sql("select * from display").collect().head.get(0)
      assume(res == "b")
    }
  }

  "train or run" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      val source =
        """
          |
          |set data='''
          |{"a":"c"}
          |{"a":"a"}
          |{"a":"k"}
          |{"a":"g"}
          |''';
          |load jsonStr.`data` as dataTable;
          |run dataTable as StringIndex.`/tmp/model1` where inputCol="a";
          |train dataTable as StringIndex.`/tmp/model2` where inputCol="a";
          |train dataTable as StringIndex.`/tmp/model3` options inputCol="a";
        """.stripMargin
      ScriptSQLExec.parse(source, sq, false)

      val source1 =
        """
          |
          |set data='''
          |{"a":"c"}
          |{"a":"a"}
          |{"a":"k"}
          |{"a":"g"}
          |''';
          |load jsonStr.`data` as dataTable;
          |run dataTable where inputCol="a" as StringIndex.`/tmp/model1`;
        """.stripMargin
      val res = intercept[RuntimeException](ScriptSQLExec.parse(source1, sq, false)).getMessage
      assume(res.startsWith("MLSQL Parser error"))
    }
  }

  "ScalaScriptUDAF" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL

      ScriptSQLExec.parse(
        """
          |set plusFun='''
          |import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
          |import org.apache.spark.sql.types._
          |import org.apache.spark.sql.Row
          |class SumAggregation extends UserDefinedAggregateFunction with Serializable{
          |    def inputSchema: StructType = new StructType().add("a", LongType)
          |    def bufferSchema: StructType =  new StructType().add("total", LongType)
          |    def dataType: DataType = LongType
          |    def deterministic: Boolean = true
          |    def initialize(buffer: MutableAggregationBuffer): Unit = {
          |      buffer.update(0, 0l)
          |    }
          |    def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
          |      val sum   = buffer.getLong(0)
          |      val newitem = input.getLong(0)
          |      buffer.update(0, sum + newitem)
          |    }
          |    def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
          |      buffer1.update(0, buffer1.getLong(0) + buffer2.getLong(0))
          |    }
          |    def evaluate(buffer: Row): Any = {
          |      buffer.getLong(0)
          |    }
          |}
          |''';
          |
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |className="SumAggregation"
          |and udfType="udaf"
          |;
          |
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |
          |-- 使用plusFun
          |select a,plusFun(a) as res from dataTable group by a as output;
        """.stripMargin, sq)
      val res = spark.sql("select * from output").collect().head.get(1)
      assume(res == 4)
    }
  }

  "PythonScriptUDAF" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL

      ScriptSQLExec.parse(
        """
          |set plusFun='''
          |from org.apache.spark.sql.expressions import MutableAggregationBuffer, UserDefinedAggregateFunction
          |from org.apache.spark.sql.types import DataTypes,StructType
          |from org.apache.spark.sql import Row
          |import java.lang.Long as l
          |import java.lang.Integer as i
          |
          |class SumAggregation:
          |
          |    def inputSchema(self):
          |        return StructType().add("a", DataTypes.LongType)
          |
          |    def bufferSchema(self):
          |        return StructType().add("total", DataTypes.LongType)
          |
          |    def dataType(self):
          |        return DataTypes.LongType
          |
          |    def deterministic(self):
          |        return True
          |
          |    def initialize(self,buffer):
          |        return buffer.update(i(0), l(0))
          |
          |    def update(self,buffer, input):
          |        sum = buffer.getLong(i(0))
          |        newitem = input.getLong(i(0))
          |        buffer.update(i(0), l(sum + newitem))
          |
          |    def merge(self,buffer1, buffer2):
          |        buffer1.update(i(0), l(buffer1.getLong(i(0)) + buffer2.getLong(i(0))))
          |
          |    def evaluate(self,buffer):
          |        return buffer.getLong(i(0))
          |''';
          |
          |
          |--加载脚本
          |load script.`plusFun` as scriptTable;
          |--注册为UDF函数 名称为plusFun
          |register ScriptUDF.`scriptTable` as plusFun options
          |className="SumAggregation"
          |and udfType="udaf"
          |and lang="python"
          |;
          |
          |set data='''
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |{"a":1}
          |''';
          |load jsonStr.`data` as dataTable;
          |
          |-- 使用plusFun
          |select a,plusFun(a) as res from dataTable group by a as output;
        """.stripMargin, sq)
      val res = spark.sql("select * from output").collect().head.get(1)
      assume(res == 4)
    }
  }

  "save-partitionby" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      val ssel = createSSEL
      val sq = new GrammarProcessListener(ssel)
      withClue("MLSQL Parser error : mismatched input 'save1' expecting {<EOF>, 'load', 'LOAD', 'save', 'SAVE', 'select', 'SELECT', 'insert', 'INSERT', 'create', 'CREATE', 'drop', 'DROP', 'refresh', 'REFRESH', 'set', 'SET', 'connect', 'CONNECT', 'train', 'TRAIN', 'run', 'RUN', 'register', 'REGISTER', 'unRegister', 'UNREGISTER', 'include', 'INCLUDE', SIMPLE_COMMENT}") {
        assertThrows[RuntimeException] {
          ScriptSQLExec.parse("save1 append skone_task_log\nas parquet.`${data_monitor_skone_task_log_2_parquet_data_path}`\noptions mode = \"Append\"\nand duration = \"10\"\nand checkpointLocation = \"${data_monitor_skone_task_log_2_parquet_checkpoint_path}\"\npartitionBy hp_stat_date;", sq)
        }
      }
      ScriptSQLExec.parse("save append skone_task_log\nas parquet.`${data_monitor_skone_task_log_2_parquet_data_path}`\noptions mode = \"Append\"\nand duration = \"10\"\nand checkpointLocation = \"${data_monitor_skone_task_log_2_parquet_checkpoint_path}\"\npartitionBy hp_stat_date;", sq)

    }
  }

}





