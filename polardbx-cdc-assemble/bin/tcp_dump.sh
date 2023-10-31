#!/bin/bash

ip_port=$1

if [ -z $2 ]; then
  monitor_time=60
else
  monitor_time=$2
fi

log_file=/tmp/tcp_dump.log
rm -f ${log_file}
touch ${log_file}

start_time=$(date +%s)
while true; do
    sudo netstat -anp |grep -w $ip_port >> ${log_file} 2>&1

    current_time=$(date +%s)
    elapsed_time=$((current_time - start_time))
    if [ $elapsed_time -ge $monitor_time ]; then
        break
    fi
done

group_by_column=5
target_column=3
awk -v group_by_col=$group_by_column -v target_col=$target_column '
    {
        key = $group_by_col
        if ($target_col > 0) {
          count[key] += 1
          sum[key] += $target_col
        }else{
          ncount[key] += 1
        }
    }

    END {
        for (key in count) {
            print key "\t" ncount[key] "\t" count[key] "\t" sum[key] "\t" sum[key]/count[key]
        }
    }
' ${log_file}
