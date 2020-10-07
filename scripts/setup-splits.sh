#!/usr/bin/env bash
set -eu

if [ "$SPLIT" == '0' ] || [ "$SPLIT" == '1' ] || [ "$SPLIT" == '2' ]
then
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:centos-7.0.15-619d118712-1.9.5-1.5.0;
elif [ "$SPLIT" == '3' ] || [ "$SPLIT" == '4' ] || [ "$SPLIT" == '5' ];
then
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:centos-6.8.15-029542cbf3-1.9.3-1.4.1;
else
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:6.7.18-db1caffe94-1.6.1-1.1.1;
fi

if [ "$SPLIT" == '0' ] || [ "$SPLIT" == '3' ] || [ "$SPLIT" == '6' ]
then
  export SPARK_VERSION=3.0.0;
elif [ "$SPLIT" == '1' ] || [ "$SPLIT" == '4' ] || [ "$SPLIT" == '7' ];
then
  export SPARK_VERSION=2.4.4;
else
  export SPARK_VERSION=2.3.4;
fi
