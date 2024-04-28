#!/bin/bash

PROG_NAME=$0
ACTION=$1

usage() {
    echo "Usage: ${PROG_NAME} [start | restart | stop]"
    exit 1;
}

if [ $# -lt 1 ]; then
    usage
fi

current_path=`pwd`
case "`uname`" in
    Linux)
		BASE_DIR=$(readlink -f $(dirname $0))
		;;
	*)
		BASE_DIR=`cd $(dirname $0); pwd`
		;;
esac
BASE_DIR=${BASE_DIR}/../
CONF_DIR=${BASE_DIR}/conf
PID_FILE=${BASE_DIR}/bin/daemon.pid

export LD_LIBRARY_PATH=${BASE_DIR}/lib/native:${LD_LIBRARY_PATH}
export LANG=zh_CN.GB18030

JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs -Dfile.encoding=UTF-8"
JAVA_OPTS="${JAVA_OPTS} -server -Xms256m -Xmx512m -Xss1m -Djute.maxbuffer=10240000"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseParallelGC"
JAVA_OPTS="${JAVA_OPTS} -XX:ParallelGCThreads=2"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=${HOME}/logs"
JAVA_OPTS="${JAVA_OPTS} -XX:+CrashOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:ErrorFile=${HOME}/hs_err_pid%p.log"
JAVA_OPTS="${JAVA_OPTS} -XX:+DisableExplicitGC"
JAVA_OPTS="${JAVA_OPTS} -Xlog:gc*:${HOME}/logs/polardbx-binlog/Daemon/gc.log:time"
JAVA_OPTS="${JAVA_OPTS} -Djava.util.prefs.systemRoot=${HOME}/.java -Djava.util.prefs.userRoot=${HOME}/.java/.userPrefs"
JAVA_OPTS="${JAVA_OPTS} -Dcdc.home.dir=${BASE_DIR}"
JAVA_OPTS="${JAVA_OPTS} -DtaskName=Daemon"
JAVA_OPTS="${JAVA_OPTS} --add-exports java.base/jdk.internal.ref=ALL-UNNAMED"

if [ -f ${HOME}/bin/env.sh ]; then
    source ${HOME}/bin/env.sh
fi

## set java path
TAOBAO_JAVA="/opt/taobao/java/bin/java"
ALIBABA_JAVA="/usr/alibaba/java/bin/java"
if [ -f $TAOBAO_JAVA ] ; then
    JAVA=$TAOBAO_JAVA
elif [ -f $ALIBABA_JAVA ] ; then
    JAVA=$ALIBABA_JAVA
else
    JAVA=$(which java)
    if [ ! -f $JAVA ]; then
            echo "Cannot find a Java JDK. Please set either set JAVA or put java (>=1.7) in your PATH." 2>&2
            exit 1
    fi
fi

start()
    {
    	#check Server Already Running
    TASK_PIDS=(`ps aux|grep -v "grep"|grep "DaemonBootStrap"|awk '{print $2}'`)
		TASK_COUNT=${#TASK_PIDS[@]}
		if [ ${TASK_COUNT} -gt 0 ] ;then
			echo "daemon Server Already Running..............!"
			exit 1;
		fi

		LOG_PATH=${HOME}/logs
		DAEMON_LOG_PATH=${LOG_PATH}/polardbx-binlog/Daemon
		START_LOG=${DAEMON_LOG_PATH}/default.log
		for jar in `ls ${BASE_DIR}/lib/*.jar`
		do
    		CLASSPATH="${CLASSPATH}:""${jar}"
		done

		CLASSPATH="${BASE_DIR}/conf:$CLASSPATH";

    if [ -f ${HOME}/env/env.properties ] ; then
        CLASSPATH="${HOME}/env:$CLASSPATH";
    fi

        echo "========================================================================="
        echo ""
        echo "  Daemon server Startup Environment"
        echo ""
        echo "  BASE_DIR: ${BASE_DIR}"
        echo ""
        echo "  JAVA_VERSION: `${JAVA} -version`"
        echo ""
        echo "  JAVA_OPTS: ${JAVA_OPTS}"
        echo ""
        echo "  CLASS_PATH: ${CLASSPATH}"
        echo ""
        echo "========================================================================="
        echo ""

        #init logPath and logFile
        if [ ! -d "${LOG_PATH}" ]; then
            mkdir ${LOG_PATH}
        fi

        if [ ! -d "${DAEMON_LOG_PATH}" ]; then
            mkdir -p ${DAEMON_LOG_PATH}
        fi

        if [ ! -f "${START_LOG}" ]; then
            touch ${START_LOG}
        fi

        #Start Java Process
        ${JAVA} ${JAVA_OPTS} ${CUSTOM_OPTS} -classpath ${CONF_DIR}:${CLASSPATH}:. com.aliyun.polardbx.binlog.daemon.DaemonBootStrap >> ${START_LOG} 2>&1 &
		#write Process Pid To File
        PID_NUM=$!
        echo ${PID_NUM} > ${PID_FILE}
    }

stop()
    {
        if [ -f "${START_LOG}" ]; then
            mv -f ${START_LOG} "${START_LOG}.`date '+%Y%m%d%H%M%S'`"
        fi

        if [ -f "${PID_FILE}" ]; then
            PID_NUM=`cat ${PID_FILE}`
            if [ "" != "${PID_NUM}" ]; then
                killWorker ${PID_NUM}
                rm -f ${PID_FILE}
            fi
        fi

        TASK_PIDS=(`ps aux|grep -v "grep"|grep "DaemonBootStrap"|awk '{print $2}'`)
		TASK_COUNT=${#TASK_PIDS[@]}
		if [ ${TASK_COUNT} -gt 0 ] ;then
			for TASK_PID in ${TASK_PIDS[*]} ;do
				killWorker ${TASK_PID}
			done
		fi
    }

killWorker()
	{
		local pid=$1
		echo kill Daemon pid is "${pid}"
		kill $pid

		cost=0
	    timeout=10
	    while [ $timeout -gt 0 ]; do
	        RUN_PID=(`ps aux|grep -v "grep"|grep "DaemonBootStrap"|awk '{print $2}'|grep ${pid}`)
	        if [ "$RUN_PID" == "" ] ; then
	            break
	        fi
	        sleep 1
	        let timeout=timeout-1
	        let cost=cost+1
	    done

	    if [ $timeout -eq 0 ] ; then
	    	echo kill -9 Daemon pid is "${pid}"
	        kill -9 ${pid}
	    fi

	    echo "stop cost ${cost}s"
	}

case "${ACTION}" in
    start)
        start
    ;;
    stop)
        stop
    ;;
    restart)
        stop
        sleep 1
        start
    ;;
    *)
        usage
    ;;
esac

cd $current_path
