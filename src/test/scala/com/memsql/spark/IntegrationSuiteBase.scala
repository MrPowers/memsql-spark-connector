package com.memsql.spark

import java.sql.{Connection, DriverManager}
import java.util.Properties

import com.github.mrpowers.spark.fast.tests.DataFrameComparer
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec

trait IntegrationSuiteBase extends AnyFunSpec with BeforeAndAfterAll with DataFrameComparer {
  final val masterHost: String     = sys.props.getOrElse("memsql.host", "localhost")
  final val masterPort: String     = sys.props.getOrElse("memsql.port", "5506")
  final val masterUser: String     = sys.props.getOrElse("memsql.user", "root")
  final val masterPassword: String = sys.props.getOrElse("memsql.password", "")

  var spark: SparkSession = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    spark = SparkSession
      .builder()
      .master("local")
      .appName("memsql-integration-tests")
      .config("spark.sql.shuffle.partitions", "1")
      .config("spark.driver.bindAddress", "localhost")
      .config("spark.datasource.memsql.masterHost", masterHost)
      .config("spark.datasource.memsql.masterPort", masterPort)
      .config("spark.datasource.memsql.user", masterUser)
      .config("spark.datasource.memsql.password", masterPassword)
      .getOrCreate()
  }

  override def afterAll(): Unit = {
    spark.close()
  }

  def jdbcConnection: Loan[Connection] = {
    val connProperties = new Properties()
    connProperties.put("user", masterUser)
    if (masterPassword != "") {
      connProperties.put("password", masterPassword)
    }

    Loan(
      DriverManager.getConnection(
        s"jdbc:mysql://$masterHost:$masterPort",
        connProperties
      ))
  }

  def executeQuery(sql: String): Unit =
    jdbcConnection.to(conn => Loan(conn.createStatement).to(_.execute(sql)))

  def jdbcOptions(dbtable: String): Map[String, String] = Map(
    "url"      -> s"jdbc:mysql://$masterHost:$masterPort",
    "dbtable"  -> dbtable,
    "user"     -> masterUser,
    "password" -> masterPassword
  )

  def jdbcOptionsSQL(dbtable: String): String =
    jdbcOptions(dbtable)
      .foldLeft(List.empty[String])({
        case (out, (k, v)) => s"'${k}'='${v}'" :: out
      })
      .mkString(", ")

  def writeTable(dbtable: String, df: DataFrame, saveMode: SaveMode = SaveMode.Overwrite): Unit =
    df.write
      .format("jdbc")
      .options(jdbcOptions(dbtable))
      .mode(saveMode)
      .save()
}