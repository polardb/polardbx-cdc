#!/bin/bash

TASK_NAME=$1
ARGS=$2
MEMORY=8192
BASE_HOME="/home/admin/polardbx-binlog.standalone"

#get param from 16th to end
JVM_PARAMS=""

usage() {
  echo "please set startup Args String"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
fi

if [ $(whoami) == "root" ]; then
  echo DO NOT use root user to launch me.
  exit 1
fi

case "$(uname)" in
Linux)
  BASE_DIR=$(readlink -f $(dirname $0))
  ;;
*)
  BASE_DIR=$(
    cd $(dirname $0)
    pwd
  )
  ;;
esac
BASE_DIR=${BASE_DIR}/../
logback_configurationFile=$BASE_DIR/conf/logback.xml
export LD_LIBRARY_PATH=${BASE_DIR}/lib:${LD_LIBRARY_PATH}
export NLS_LANG=AMERICAN_AMERICA.ZHS16GBK


JAVA_OPTS="${JAVA_OPTS} -server -Xms${MEMORY}m -Xmx${MEMORY}m -Xss1m -Djute.maxbuffer=10240000 -DtaskName=$TASK_NAME -Dlogback.configurationFile=$logback_configurationFile"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseParallelGC"
JAVA_OPTS="${JAVA_OPTS} -XX:-UseAdaptiveSizePolicy -XX:SurvivorRatio=2 -XX:NewRatio=1 -XX:ParallelGCThreads=6"
JAVA_OPTS="${JAVA_OPTS} -XX:-OmitStackTraceInFastThrow"

JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -XX:+DisableExplicitGC"
JAVA_OPTS="${JAVA_OPTS} -Xlog:gc*:${BASE_DIR}/../logs/gc-${TASK_NAME}.log:time"
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -Dcdc.home.dir=${BASE_HOME}"

if [ -f /home/admin/env.properties ]; then
    for line in `cat /home/admin/env.properties`
    do
        JAVA_OPTS="${JAVA_OPTS} -D$line"
    done
fi

if [ ! -d ${HOME}/.java ]; then
  mkdir "${HOME}/.java"
fi

if [ ! -d ${HOME}/.java/.userPrefs ]; then
  mkdir "${HOME}/.java/.userPrefs"
fi

if [ ! -d ${HOME}/.java/.systemPrefs ]; then
  mkdir "${HOME}/.java/.systemPrefs"
fi

JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} ${JVM_PARAMS}"

## set java path
TAOBAO_JAVA="/opt/taobao/java/bin/java"
ALIBABA_JAVA="/usr/alibaba/java/bin/java"
if [ -f $TAOBAO_JAVA ]; then
  JAVA=$TAOBAO_JAVA
elif [ -f $ALIBABA_JAVA ]; then
  JAVA=$ALIBABA_JAVA
else
  JAVA=$(which java)
  if [ ! -f $JAVA ]; then
    echo "Cannot find a Java JDK. Please set either set JAVA or put java (>=1.7) in your PATH." 2>&2
    exit 1
  fi
fi

#BUILDER IN classpath
CLASSPATH=""

for jar in $(ls ${BASE_DIR}/lib/*.jar); do
  CLASSPATH="${CLASSPATH}:""${jar}"
done

CLASSPATH="${BASE_DIR}/conf:$CLASSPATH";

#delete diamond data
#DIAMOND_DATA_PATH=$HOME/diamond/default_diamond/snapshot
#rm -rf ${DIAMOND_DATA_PATH}/*

if [ ! -d $HOME/logs/polardbx-binlog/$TASK_NAME ]; then
  mkdir $HOME/logs/polardbx-binlog/$TASK_NAME
fi
defaultLog=$HOME/logs/polardbx-binlog/$TASK_NAME/default.log

#change user.dir to /home/admin
cd $HOME

#Start Java Process

if [[ "$TASK_NAME" == "RpcSimulator" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.stress.FromRpcServerStressSimulator "${ARGS}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == Dumper* ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.dumper.DumperBootStrap "${ARGS}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == "TransmitterSimulator" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.stress.FromTransmitterStressSimulator "${ARGS}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == "MysqlDumpStressTest" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.stress.MysqlDumpStressTest "${ARGS}" 1>>$defaultLog 2>&1 &
else
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.stress.FromMergerStressSimulator "${ARGS}" 1>>$defaultLog 2>&1 &
fi
