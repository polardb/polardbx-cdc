#!/bin/bash

### ====================================================================== ###
##                                                                        	##
##  Polardbx-rpl Task Startup Script										##
##                                                                          ##
### ====================================================================== ###
### 2020-08-08 by chengjin
### make some different

TASK_ID=$1
TASK_NAME=$2
MEMORY=$3
YOUNG_MEMORY=$(($3*5/8))
LOG_DIR=$HOME/logs/polardbx-rpl/$TASK_NAME

#get param from 16th to end
JVM_PARAMS=""

usage() {
  echo "please set startup Args String"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
fi


case "$(uname)" in
Linux)
  BASE_HOME=$(readlink -f $(dirname $0))
  ;;
*)
  BASE_HOME=$(
    cd $(dirname $0)
    pwd
  )
  ;;
esac
BASE_HOME=${BASE_HOME}/../


export LD_LIBRARY_PATH=${BASE_HOME}/lib/native:${LD_LIBRARY_PATH}
export NLS_LANG=AMERICAN_AMERICA.ZHS16GBK
export LANG=zh_CN.GB18030

logback_configurationFile=${BASE_HOME}/conf/rpl-logback.xml

JAVA_OPTS="${JAVA_OPTS} -server -Xms${MEMORY}m -Xmx${MEMORY}m -Xmn${YOUNG_MEMORY}m -Xss1m -Djute.maxbuffer=10240000 -DtaskId=$TASK_ID -Dlogback.configurationFile=$logback_configurationFile"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseParallelGC"
JAVA_OPTS="${JAVA_OPTS} -XX:SurvivorRatio=2 -XX:ParallelGCThreads=6"
JAVA_OPTS="${JAVA_OPTS} -XX:-OmitStackTraceInFastThrow"

JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -XX:+DisableExplicitGC"
JAVA_OPTS="${JAVA_OPTS} -Xlog:gc*:${LOG_DIR}/gc.log:time"
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -Dcdc.home.dir=${BASE_HOME}"
JAVA_OPTS="${JAVA_OPTS} -Djava.io.tmpdir=/tmp/${TASK_ID}"
JAVA_OPTS="${JAVA_OPTS} --add-exports java.base/jdk.internal.ref=ALL-UNNAMED"

if [ -f /home/admin/env.properties ]; then
    for line in `cat /home/admin/env.properties`
    do
        JAVA_OPTS="${JAVA_OPTS} -D$line"
    done
fi

if [ ! -d ${HOME}/.java ]; then
  mkdir -p "${HOME}/.java"
fi

if [ ! -d ${HOME}/.java/.userPrefs ]; then
  mkdir -p "${HOME}/.java/.userPrefs"
fi

if [ ! -d ${HOME}/.java/.systemPrefs ]; then
  mkdir -p "${HOME}/.java/.systemPrefs"
fi

JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=${HOME}/logs"
JAVA_OPTS="${JAVA_OPTS} -XX:+CrashOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:ErrorFile=${LOG_DIR}/hs_err_pid%p.log"

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
CLASSPATH="${BASE_HOME}/conf";

if [ -f ${HOME}/env/env.properties ]; then
  CORES=$(sed '/^cpu_cores=/!d;s/.*=//' ${HOME}/env/env.properties)
  if [ -n "$CORES" ]; then
     JAVA_OPTS="${JAVA_OPTS} -XX:ActiveProcessorCount=$CORES"
  fi
  CLASSPATH="${HOME}/env:$CLASSPATH"
fi

for jar in $(ls ${BASE_HOME}/lib/*.jar); do
  CLASSPATH="${CLASSPATH}:""${jar}"
done

if [ ! -d ${LOG_DIR} ]; then
  mkdir -p ${LOG_DIR}
fi
defaultLog=${LOG_DIR}/default.log

#change user.dir to /home/admin
cd $HOME

#Start Java Process
echo "$(date +"%Y-%m-%d-%H:%M:%S.%N") ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.rpl.RplTaskEngine "taskId=${TASK_ID}"" >>$defaultLog
${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.rpl.RplTaskEngine "taskId=${TASK_ID} taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
