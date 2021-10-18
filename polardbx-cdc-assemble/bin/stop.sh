#!/bin/bash

TASK_NAME=$1

usage() {
  echo "please set stop Args taskName(String)"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
fi

if [ $(whoami) == "root" ]; then
  echo DO NOT use root user to launch me.
  exit 1
fi

defaultLog=$HOME/logs/polardbx-binlog/$TASK_NAME/default.log

pid=$(ps -ux | grep taskName=$TASK_NAME | grep -v grep | awk '{print $2}')

if [ "$pid" -gt 0 ] 2>/dev/null; then
  echo "will stop $pid $taskName..."
  kill -9 "$pid"
  echo "$(date "+%Y-%m-%d %H:%M:%S") force stop $TASK_NAME..." >>defaultLog
else
  echo "no pid for $taskName to stop."
fi
