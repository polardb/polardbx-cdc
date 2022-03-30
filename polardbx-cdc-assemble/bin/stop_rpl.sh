#!/bin/bash

### ====================================================================== ###
##                                                                        	##
##  Polardbx-rpl Task Stop Script											##
##                                                                          ##
### ====================================================================== ###

TASK_ID=$1

BASE_HOME="/home/admin/polardbx-binlog.standalone"

usage() {
  echo "please set stop Args taskId"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
fi

if [ $(whoami) == "root" ]; then
  echo DO NOT use root user to launch me.
  exit 1
fi


runningCount=`ps -ef | grep RplTaskEngine | grep taskId=${TASK_ID} | wc -l`
echo "taskId: ${TASK_ID} running count: $runningCount"

if [ "$runningCount" -ge 1 ];then
    echo "taskId: ${TASK_ID} already running, kill all and restart"
    for pid in `ps -ef | grep RplTaskEngine | grep taskId=${TASK_ID} | awk '{print $2}'` ; do kill -9 $pid ; done
else
  echo "no pid for $TASK_ID to stop."
fi

