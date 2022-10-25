#!/usr/bin/env bash
PATH="/opt/taobao/java/bin:/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin"
# This file is used by cron table

WORKER_BIN=/home/admin/polardbx-binlog.standalone/bin
needStart=0;

PID_FILE="daemon.pid";
executeShell="daemon.sh";

if [ ! -f "$WORKER_BIN"/${PID_FILE} ];then
     echo "${PID_FILE} not exist"
     needStart=1;
else
    pid=`cat $WORKER_BIN/${PID_FILE}`
    echo "current ${PID_FILE} value : $pid";
    if [ -z "$pid" ]; then
        echo "${PID_FILE} exist, but pid is empty"
        needStart=1;
    else
        runningPid=`ps -ef | grep java | awk '{print $2}' | grep "$pid" |  awk '{print $1}'`;
        echo "Running pid: $runningPid"
        if  [ "$pid" != "$runningPid" ]; then
            echo "pid not empty, but process not exist"
            needStart=1;
        fi
    fi
fi

runningCount=`ps -ef | grep java | grep DaemonBootStrap | wc -l`
echo "Running ${PID_FILE} count: $runningCount"
if [ "$runningCount" -gt 1 ];then
    echo "Running ${PID_FILE} count more than 1";
    needStart=1;
fi

if [ "$needStart" -eq 1 ];then
    echo "Retarting ${PID_FILE}";
    sh $WORKER_BIN/${executeShell} restart > /dev/null 2>&1 &
fi
