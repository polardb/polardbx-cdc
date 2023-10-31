#!/bin/bash

num_loops=$1
group_name=$2
if [ -z $3 ]; then
  sleep_time=300
else
  sleep_time=$3
fi

counter=0

log_file=/tmp/rebalance.log
echo "log file path "${log_file}
if [ ! -f "${log_file}" ]; then
  touch ${log_file}
fi

while [ $counter -lt $num_loops ]; do
    mysql_command="mysql -hxxx  -uxxx -pxxx -Pxxx"
    echo "rebalance master" | `$mysql_command` >> ${log_file} 2>&1
    echo "rebalance master success. "`date` >> ${log_file} 2>&1 &

    if [ -n "$group_name" ]; then
      rebalance_sql="rebalance master with "$group_name
      echo $rebalance_sql | `$mysql_command` >> ${log_file} 2>&1
      echo $rebalance_sql" success. "`date` >> ${log_file} 2>&1 &
    fi

    sleep $sleep_time
    ((counter++))
done
echo "rebalance runs end" >> ${log_file} 2>&1 &
