#!/usr/bin/env bash
curl '192.168.1.81:8090/jobs?appName=nameconnector&classPath=org.apache.spark.sql.execution.datasources.hbase.examples.HBaseFilter&context=test-context&sync=true&timeout=100000' -d "{
       StartTime=\"1\",
       EndTime=\"1482528495\",
       Threshold=0.3
       CameraList=\"all\"
   }"
