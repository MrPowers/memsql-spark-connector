#!/usr/bin/env bash
set -eu

TEST_NUM="${1:-"0"}"

if [ "$TEST_NUM" == '0' ] || [ "$TEST_NUM" == '1' ] || [ "$TEST_NUM" == '2' ]
then
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:centos-7.0.15-619d118712-1.9.5-1.5.0
elif [ "$TEST_NUM" == '3' ] || [ "$TEST_NUM" == '4' ] || [ "$TEST_NUM" == '5' ]
then
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:centos-6.8.15-029542cbf3-1.9.3-1.4.1
else
  export MEMSQL_IMAGE=memsql/cluster-in-a-box:6.7.18-db1caffe94-1.6.1-1.1.1
fi

# start memsql cluster
./scripts/ensure-test-memsql-cluster.sh

# run tests
if [ "$TEST_NUM" == '0' ] || [ "$TEST_NUM" == '3' ] || [ "$TEST_NUM" == '6' ]
then
  sbt ++2.12.12 test -Dspark.version=3.0.0
elif [ "$TEST_NUM" == '1' ] || [ "$TEST_NUM" == '4' ] || [ "$TEST_NUM" == '7' ]
then
  sbt ++2.11.11 "testOnly -- -l  OnlySpark3" -Dspark.version=2.4.4
else
  sbt ++2.11.11 "testOnly -- -l  OnlySpark3" -Dspark.version=2.3.4
fi

