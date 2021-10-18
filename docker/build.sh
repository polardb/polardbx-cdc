#!/bin/bash

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
docker build --no-cache -t polardbx/polardbx-cdc $BASE/
