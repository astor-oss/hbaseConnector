#!/usr/bin/env bash

SPARK_HOME=/mnt/huzongxing/spark-1.6.1-bin-hadoop2.6
export LD_LIBRARY_PATH=/usr/lib/face_libso
LOGCONF=/mnt/huzongxing/spark-1.6.1-bin-hadoop2.6/conf/log4j.properties

$SPARK_HOME/bin/spark-submit --master local[*] \
    --driver-java-options "-Dlog4j.configuration="$LOGCONF \
    --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=file:"$LOGCONF \
    --num-executors 1 \
    --executor-cores 10 \
    --executor-memory 32g \
    --jars  /usr/hdp/current/hbase-client/lib/htrace-core-3.1.0-incubating.jar,/usr/hdp/current/hbase-client/lib/hbase-client.jar,/usr/hdp/current/hbase-client/lib/hbase-common.jar,/usr/hdp/current/hbase-client/lib/hbase-server.jar,/usr/hdp/current/hbase-client/lib/guava-12.0.1.jar,/usr/hdp/current/hbase-client/lib/hbase-protocol.jar,/usr/hdp/current/hbase-client/lib/htrace-core-3.1.0-incubating.jar,./jars/amjcmatch-3.0.2-1.2.jar,./jars/amjcmatch-3.0.2-1.2-linux-x86_64.jar,./jars/javacpp-1.2.jar \
    --files conf/hbase-site.xml \
    --class org.apache.spark.sql.execution.datasources.hbase.examples.HBaseScan \
    /mnt/huzongxing/shc/target/hbase-spark-connector-1.0.0.jar  \
    --StartTime=1\
    --EndTime=1472182107 \
    --CamerList=all \
    --PartitionNum=200 \
    --Threshold=1.5 \
    --duration=2
