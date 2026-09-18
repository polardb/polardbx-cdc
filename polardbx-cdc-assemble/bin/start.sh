#!/bin/bash

TASK_NAME=$1
MEMORY=$2
if [ -z "$MEMORY" ]; then
    MEMORY=1024
    PERM_MEMORY=128
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
export LD_LIBRARY_PATH=${BASE_DIR}/lib/native:${LD_LIBRARY_PATH}
export NLS_LANG=AMERICAN_AMERICA.ZHS16GBK
export LANG=zh_CN.GB18030

JAVA_OPTS="${JAVA_OPTS} -server -Xms${MEMORY}m -Xmx${MEMORY}m -Xss1m -DtaskName=$TASK_NAME -Dlogback.configurationFile=$logback_configurationFile"
if [[ ! "$JVM_PARAMS" =~ "PermSize" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -XX:PermSize=${PERM_MEMORY}m -XX:MaxPermSize=${PERM_MEMORY}m"
fi

# 移除CMS垃圾回收器相关参数，使用G1垃圾回收器替代
# 原来的CMS参数:
# JAVA_OPTS="${JAVA_OPTS} -XX:+UseConcMarkSweepGC -XX:-UseAdaptiveSizePolicy -XX:SurvivorRatio=2 -XX:NewRatio=2 -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
# G1 的停顿时间目标（毫秒）。G1会根据它来调节年轻代大小、每次混合回收的老年代 Region 数等。是“目标”而非严格保证。
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"
# 启用G1垃圾回收器的“Concurrent Cycle”（并发标记+后续 Mixed GC），而不是立即 Full GC，降低因显式 GC 造成的长时间停顿。
JAVA_OPTS="${JAVA_OPTS} -XX:+ExplicitGCInvokesConcurrent"
# JVM 启动时预触碰全部内存页，把虚拟内存提前映射为物理页，降低后续运行中的缺页中断和内存碎片对停顿的影响。配合大页更有效
# 开启此参数会导致启动变慢，但运行期间更稳定 (实验室反应开启该参数会导致反复重启，因此禁用)
# JAVA_OPTS="${JAVA_OPTS} -XX:+AlwaysPreTouch"
# G1MaxNewSizePercent 年轻代的最大百分比上限（占总堆）。限制 G1 自适应扩大的天花板。
# JAVA_OPTS="${JAVA_OPTS} -XX:+UnlockExperimentalVMOptions -XX:G1MaxNewSizePercent=50"


JAVA_OPTS="${JAVA_OPTS} -XX:-OmitStackTraceInFastThrow"
JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -Xlog:gc*:${HOME}/logs/polardbx-binlog/$TASK_NAME/gc.log:time"
JAVA_OPTS="${JAVA_OPTS} -Dmemory=${MEMORY}"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -Dcdc.home.dir=${BASE_DIR}"
JAVA_OPTS="${JAVA_OPTS} --add-exports java.base/jdk.internal.ref=ALL-UNNAMED"

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
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=${HOME}/logs"
JAVA_OPTS="${JAVA_OPTS} -XX:+CrashOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:ErrorFile=${HOME}/logs/polardbx-binlog/$TASK_NAME/hs_err_pid%p.log"

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

if [[ "$TASK_NAME" == Dumper* ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.dumper.DumperBootStrap "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
elif [[ "$TASK_NAME" == "TRANSFER" ]]; then
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.transfer.Main "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
else
  ${JAVA} ${JAVA_OPTS} -classpath ${CLASSPATH}:. com.aliyun.polardbx.binlog.TaskBootStrap "taskName=${TASK_NAME}" 1>>$defaultLog 2>&1 &
fi