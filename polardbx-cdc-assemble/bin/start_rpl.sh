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
MEMORY=3072
PERM_MEMORY=256
BASE_HOME=$HOME/polardbx-binlog.standalone
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

if [ $(whoami) == "root" ]; then
  echo DO NOT use root user to launch me.
  exit 1
fi

export LD_LIBRARY_PATH=${BASE_HOME}/lib:${LD_LIBRARY_PATH}
export NLS_LANG=AMERICAN_AMERICA.ZHS16GBK
export LANG=zh_CN.GB18030

if [ -f ${HOME}/bin/jdk8.sh ]; then
    sudo sh ${HOME}/bin/jdk8.sh
fi

logback_configurationFile=${BASE_HOME}/conf/rpl-logback.xml

JAVA_OPTS="${JAVA_OPTS} -server -Xms${MEMORY}m -Xmx${MEMORY}m -Xss1m -Djute.maxbuffer=10240000 -DtaskId=$TASK_ID -Dlogback.configurationFile=$logback_configurationFile"
if [[ ! "$JVM_PARAMS" =~ "PermSize" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -XX:PermSize=${PERM_MEMORY}m -XX:MaxPermSize=${PERM_MEMORY}m"
fi
#JAVA_OPTS="${JAVA_OPTS} -XX:NewSize=128m -XX:MaxNewSize=256m"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseParallelGC"
JAVA_OPTS="${JAVA_OPTS} -XX:-UseAdaptiveSizePolicy -XX:SurvivorRatio=2 -XX:NewRatio=1 -XX:ParallelGCThreads=6"
JAVA_OPTS="${JAVA_OPTS} -XX:-OmitStackTraceInFastThrow"

JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDetails"
JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDateStamps"
JAVA_OPTS="${JAVA_OPTS} -XX:+DisableExplicitGC"
JAVA_OPTS="${JAVA_OPTS} -Xloggc:${LOG_DIR}/gc.log"
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -Ddaemon.home.dir=${BASE_HOME}"

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
