/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.hbase.examples

import java.util

import akka.actor._
import akka.actor.ActorSystem
import akka.util.Timeout

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.{SQLContext, _}
import org.apache.spark.sql.execution.datasources.hbase._
import org.apache.spark.{SparkConf, SparkContext}
import spark.jobserver.{JobServerNamedObjects, NamedObjectSupport}
import spark.jobserver.{DataFramePersister, NamedObjectPersister}
import spark.jobserver.NamedDataFrame
import spark.jobserver._
import org.bytedeco.javacpp.amjcmatch._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{BooleanType, IntegerType, StructField, StructType}
import org.apache.spark.storage.StorageLevel

import scala.util.Try

object HBaseLoad extends SparkJob with NamedObjectSupport {
  implicit def rddPersister: NamedObjectPersister[NamedRDD[Row]] = new RDDPersister[Row]
  implicit val dataFramePersister = new DataFramePersister

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = SparkJobValid

  override def runJob(sc: SparkContext, config: Config): Any = {
    // 1. get the input params
    val config = ConfigFactory.parseString("")
    val partionNum = {
      val defaultPartition:Int = 200
      try {
        config.getInt("PartitionNum")
      } catch {
        case _:Throwable => defaultPartition
      }
    }
    val hbaseTableName = {
      val defaultTableName = "facedb:FaceLog4"
      try {
        config.getString("LoadTableName")
      } catch {
        case _:Throwable => defaultTableName
      }
    }
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam:: PartitionNum[" + partionNum + "]")
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam:: LoadTableName[" + hbaseTableName + "]")

    // 2. init
    sc.getConf.setAppName("DBBuildIndex")
    val sqlContext = new SQLContext(sc)

    // 3. load hdfs data
    val startLoadTime = System.currentTimeMillis()
    def withCatalog(cat: String): DataFrame = {
      sqlContext
        .read
        .options(Map(HBaseTableCatalog.tableCatalog->cat))
        .format("org.apache.spark.sql.execution.datasources.hbase")
        .load()
    }

    val cat = s"""{
                  |"table":{"namespace":"facedb", "name":"facedb:FaceLog4"},
                  |"rowkey":"key",
                  |"columns":{
                  |"CKey":{"cf":"rowkey", "col":"key", "type":"string"},
                  |"CFeature":{"cf":"FEATURE", "col":"feature", "type":"binary"}
                  |}
                  |}""".stripMargin.replace("facedb:FaceLog4", hbaseTableName)

    val df = withCatalog(cat).repartition(partionNum.toInt).cache()
    val totalRecordCnt = df.count()
    val endLoadTime = System.currentTimeMillis()
    namedObjects.update("HBaseDataFrame", NamedDataFrame(df, false, StorageLevel.MEMORY_AND_DISK))
    Logger.getLogger(getClass).log(Level.INFO, "------------------LLLLLLLLoadTimeUsed:-----TotalCnt:["+ totalRecordCnt + "], ------------timecost["+(endLoadTime - startLoadTime)+"]--------------")

    /*
    import scala.concurrent.duration._
    implicit val timeout = akka.util.Timeout(1000000 millis)
    val key = "Companion"
    val rdd = namedRdds.getOrElseCreate(getClass.getSimpleName, {
      // anonymous generator function
      sc.parallelize(1 to 50000000).map(x => (key, key.length))
    })
    this.namedRdds.update("french_dictionary", rdd)

    // RDD should already be in cache the second time
    val rdd2 = namedRdds.get[Int]("french_dictionary")
    assert(rdd2 == Some(rdd), "Error: " + rdd2 + " != " + Some(rdd))
    val sum = rdd.map { x => x._2}.collect().sum
    Logger.getLogger(getClass).log(Level.INFO, "================>total rdd2 count: " + sum)
    */
  }
  private def rows(sc: SparkContext): RDD[Row] = {
    sc.parallelize(List(Row(1, true), Row(2, false), Row(55, true)))
  }

}
