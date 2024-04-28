#!/bin/bash
PLATFORM=$1
current_path=`pwd`
case "`uname`" in
    Darwin)
        bin_abs_path=`cd $(dirname $0); pwd`
        ;;
    Linux)
        bin_abs_path=$(readlink -f $(dirname $0))
        ;;
    *)
        bin_abs_path=`cd $(dirname $0); pwd`
        ;;
esac
BASE=${bin_abs_path}

rm -rf $BASE/polardbx-binlog.tar.gz
cd $BASE/../ && mvn clean package -Dmaven.test.skip -Prelease
cp $BASE/../polardbx-cdc-assemble/target/polardbx-binlog.tar.gz $BASE/


if [ ! $PLATFORM ]; then
  echo "build with x86"
  docker build --platform amd64 --no-cache -t polardbx/polardbx-cdc $BASE/
else
  if [[ "$PLATFORM" == arm64 ]]; then
    echo "build with arm64"
    docker build --platform arm64 --no-cache -t polardbx/polardbx-cdc-arm $BASE/
  else
    echo "build with x86"
    docker build --platform amd64 --no-cache -t polardbx/polardbx-cdc $BASE/
  fi
fi



