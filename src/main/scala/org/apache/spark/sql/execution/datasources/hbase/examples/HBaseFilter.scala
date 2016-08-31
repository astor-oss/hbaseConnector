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

import spark.jobserver.{NamedObjectSupport, JobServerNamedObjects}
import spark.jobserver.{NamedObjectPersister, DataFramePersister}
import spark.jobserver.NamedDataFrame
import spark.jobserver._

import org.bytedeco.javacpp.amjcmatch._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.storage.StorageLevel

import scala.util.Try

object HBaseFilter extends SparkJob with NamedObjectSupport {
  implicit def rddPersister: NamedObjectPersister[NamedRDD[Row]] = new RDDPersister[Row]
  implicit val dataFramePersister = new DataFramePersister
  type JobOutput = Array[String]

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = {
    // 1. query start & end time
    Try(config.getString("StartTime"))
      .map(x => SparkJobValid)
      .getOrElse(SparkJobInvalid("No StartTime config param"))

    Try(config.getString("EndTime"))
      .map(x => SparkJobValid)
      .getOrElse(SparkJobInvalid("No EndTime config param"))

    // 2. get cameralist
    // 2.1 CameraList=1,2,3,4
    // 2.2 CameraList=all
    Try(config.getString("CameraList"))
      .map(x => SparkJobValid)
      .getOrElse(SparkJobInvalid("No CameraList config param"))

    // 3. threshold
    Try(config.getDouble("Threshold"))
      .map(x => SparkJobValid)
      .getOrElse(SparkJobInvalid("No threshold config param"))
  }

  override def runJob(sc: SparkContext, config: Config): JobOutput = {
    // 1. get the input params
    val strStartTime = config.getString("StartTime")
    val strEndTime = config.getString("EndTime")
    val inputCameraList = config.getString("CameraList")
    val threshold = config.getDouble("Threshold")
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam: StartTime[" + strStartTime + "]")
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam: EndTime[" + strEndTime + "]")
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam:: Threshold[" + threshold + "]")

    val localCameraList = {
        if (inputCameraList == "all") {
          (0 to 999).map{i => s"${i}"}.toList
        } else {
          inputCameraList.split(",").map(x => x.toInt).toList
        }
    }
    Logger.getLogger(getClass).log(Level.INFO, "=====>InputParam: cameraList[" + localCameraList + "]")

    // 2. init
    sc.getConf.setAppName("DBQuery")
    val sqlContext = new SQLContext(sc)

    // 3. load name DataFrame & cal count
    val startScanTime = System.currentTimeMillis()
    val NamedDataFrame(df, _, _) = namedObjects.get[NamedDataFrame]("HBaseDataFrame").get
    val totalDFCnt = df.count()
    val endScanTime = System.currentTimeMillis()
    Logger.getLogger(getClass).log(Level.INFO, "=====>TotalRecordCnt[" +totalDFCnt +"] Time cost[" +(endScanTime - startScanTime)+ "]")
    Logger.getLogger(getClass).log(Level.INFO, "=====>DataFrame schema+[" + df.schema.toString() + "]")

    // 4. filter all record & get the records in [StartTime, EndTime] & CameraList in (1,2,3,4)
    val selectResult = df.select(
      df.col("CKey").substr(1,3).alias("camerid"),
      df.col("CKey").substr(4, 10).alias("timestamp"),
      df.col("CKey"),
      df.col("CFeature"))

    val filterResult = selectResult.filter((
      selectResult.col("camerid").isin(localCameraList:_*)) &&
      (selectResult.col("timestamp") >= strStartTime && selectResult.col("timestamp")<= strEndTime)).
      select(selectResult.col("CKey"), selectResult.col("CFeature"))

    val timeCameraCnt = filterResult.count()
    // todo add real feature
    def matchFilter(param: (String, Array[Byte])): Boolean = {
      val value = AmJCMatch(param._2, param._2)
      if (value > threshold) {
        Logger.getLogger(getClass).log(Level.INFO, "======>Key[" + param._1 + "], FeatureValue[" + value + "]")
      }
      (AmJCMatch(param._2, param._2) > threshold)
    }

    val filterRDD = filterResult.map(r => {
      (r.getAs[String]("CKey"), r.getAs[Array[Byte]]("CFeature"))
    }).filter(matchFilter)
    val threholdCnt = filterRDD.count()
    Logger.getLogger(getClass).log(Level.INFO, "=====>TimeAndCameraFilterCnt+[" + timeCameraCnt + "], Threshold FilterCnt[" + threholdCnt + "]")

    // 5. return all the rowid
    filterRDD.map(x => x._1).toArray()
  }
  def compareFeature(row: Row/*,feature:Array[Byte]*/):Double = {
    val rFeature = row.getAs[Array[Byte]]("CFeature")
    AmJCMatch(rFeature, rFeature)
  }
}
