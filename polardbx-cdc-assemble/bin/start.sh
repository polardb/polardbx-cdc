#!/bin/bash

TASK_NAME=$1
MEMORY=$2
if [ -z "$MEMORY" ]; then
  if [[ "$TASK_NAME" == "DAEMON" ]]; then
    MEMORY=256
    PERM_MEMORY=64
  else
    MEMORY=1024
    PERM_MEMORY=128
  fi
fi

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
export LANG=zh_CN.GB18030

if [ -f ${HOME}/bin/jdk8.sh ]; then
    sudo sh ${HOME}/bin/jdk8.sh
fi

JAVA_OPTS="${JAVA_OPTS} -server -Xms${MEMORY}m -Xmx${MEMORY}m -Xss1m -DtaskName=$TASK_NAME -Dlogback.configurationFile=$logback_configurationFile"
if [[ ! "$JVM_PARAMS" =~ "PermSize" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -XX:PermSize=${PERM_MEMORY}m -XX:MaxPermSize=${PERM_MEMORY}m"
fi
JAVA_OPTS="${JAVA_OPTS} -XX:+UseParallelGC"
JAVA_OPTS="${JAVA_OPTS} -XX:-UseAdaptiveSizePolicy -XX:SurvivorRatio=2 -XX:NewRatio=1 -XX:ParallelGCThreads=6"
JAVA_OPTS="${JAVA_OPTS} -XX:-OmitStackTraceInFastThrow"

JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDetails"
JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDateStamps"
JAVA_OPTS="${JAVA_OPTS} -Xloggc:${HOME}/logs/polardbx-binlog/$TASK_NAME/gc.log"
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -Ddaemon.home.dir=${BASE_DIR}"

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
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=${BASE_DIR}/logs"


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

CLASSPATH="${BASE_DIR}/conf:$CLASSPATH"

if [ -f ${HOME}/env/env.properties ]; then
  CORES=$(sed '/^cpu_cores=/!d;s/.*=//' ${HOME}/env/env.properties)
  if [ -n "$CORES" ]; then
     JAVA_OPTS="${JAVA_OPTS} -XX:ActiveProcessorCount=$CORES"
  fi
  CLASSPATH="${HOME}/env:$CLASSPATH"
fi


if [ ! -d $HOME/logs/polardbx-binlog/$TASK_NAME ]; then
  mkdir $HOME/logs/polardbx-binlog/$TASK_NAME
fi
defaultLog=$HOME/logs/polardbx-binlog/$TASK_NAME/default.log

cd $HOME

#Start Java Process
if [[ "$TASK_NAME" == "DAEMON" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.daemon.DaemonBootStrap "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == Dumper* ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.dumper.DumperBootStrap "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == "TRANSFER" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.transfer.Main "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
else
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.TaskBootStrap "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
fi
