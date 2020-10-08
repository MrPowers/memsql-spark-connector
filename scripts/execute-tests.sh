#!/usr/bin/env bash
set -eu

TEST_NUM=${SPLIT:-"0"}

if [ "$TEST_NUM" == '0' ] || [ "$TEST_NUM" == '3' ] || [ "$TEST_NUM" == '6' ]
then
  sbt ++2.12.12 test -Dspark.version=3.0.0 -J-Xmx5g -J-Xms1m
elif [ "$TEST_NUM" == '1' ] || [ "$TEST_NUM" == '4' ] || [ "$TEST_NUM" == '7' ]
then
  sbt ++2.11.11 "testOnly -- -l  OnlySpark3" -Dspark.version=2.4.4 -J-Xmx5g -J-Xms1m
else
  sbt ++2.11.11 "testOnly -- -l  OnlySpark3" -Dspark.version=2.3.4 -J-Xmx5g -J-Xms1m
fi

