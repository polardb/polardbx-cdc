#!/bin/bash

### ====================================================================== ###
##                                                                        	##
##  Polardbx-rpl Task Startup Script										##
##                                                                          ##
### ====================================================================== ###
### 2020-08-08 by chengjin
### make some different

echo "*****************************"
echo "params examples"
echo "clearPosition [serviceId]"
echo "start [serviceId]"
echo "stop [serviceId]"
echo "*****************************"

OP=$1
SERVICE_ID=$2

MEMORY=256
PERM_MEMORY=128
BASE_HOME=$HOME/polardbx-rpl.standalone

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
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs"
JAVA_OPTS="${JAVA_OPTS} -Dcdc.home.dir=${BASE_HOME}"

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
CLASSPATH=""

for jar in $(ls ${BASE_HOME}/lib/*.jar); do
  CLASSPATH="${CLASSPATH}:""${jar}"
done

#change user.dir to /home/admin
cd $HOME

#Start Java Process
echo "${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.RplTool "op=${OP} serviceId=${SERVICE_ID}"
${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.RplTool "op=${OP} serviceId=${SERVICE_ID}"

