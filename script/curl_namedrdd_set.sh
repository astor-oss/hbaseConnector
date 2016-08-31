#!/usr/bin/env bash
curl '192.168.1.81:8090/jobs?appName=nameconnector&classPath=org.apache.spark.sql.execution.datasources.hbase.examples.HBaseLoad&context=test-context&sync=true' -d "{ }"
