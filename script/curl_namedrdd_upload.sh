#!/usr/bin/env bash

curl --data-binary @/mnt/huzongxing/shc/target/hbase-spark-connector-1.0.0.jar  192.168.1.81:8090/jars/nameconnector

curl 192.168.1.81:8090/jars
