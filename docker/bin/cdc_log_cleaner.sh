#!/usr/bin/env bash

cdc_log_path='/home/admin/logs'
cleaner_log='/home/admin/.bin/cdc_cleaner.log'

if [ -z $1 ]; then
    max_used=30
else
    max_used=$1
fi

cd $cdc_log_path

print() {
    if [ -z $2 ]; then
        echo $1 >> $cleaner_log
    else
        echo "level[$2] $1" >> $cleaner_log
    fi
}


if [ -z "`ls -A $cdc_log_path`" ]; then
   print "$cdc_log_path is empty!"
   exit
else
   print "handle: $cdc_log_path"
fi

get_dir_size() {
    raw_amount=`du -s $cdc_log_path --exclude="rdsbinlog" --exclude="rocksdb" --exclude="rocksdb_x" --exclude="rocksdb_meta" --exclude="rocksdb_rpl" --exclude="binlogdump" | awk '{ print $1}' `
    temp=`echo $raw_amount | awk '{printf("%.2f\n",$1/1024^2)}' `
    use=$( printf "%.0f" $temp)
    return $use
}

clean_process_log() {
    current_process_path=$1
    if [ -z "`ls -A $current_process_path`" ]; then
        return
    else
        print "handle: $current_process_path" $2
    fi

    ls -lhtr $current_process_path|sed '1d'|while read LINE
    do
        db_path="$current_process_path/`echo $LINE|awk '{print $9}'`"
        clean_db_log $db_path $2

        get_dir_size
        if [ $use -lt $max_used ]; then
            return $use
        fi
    done
}

clean_db_log() {
    if [ -z "`ls -A $1`" ]; then
        return $use
    else
        print "handle: $1" $2
    fi

    ls -hl $1/*.tmp| awk '{print $9}' |while read LINE
    do
        clean_log "$LINE" $2
        if [ $use -lt $max_used ]; then
            return $use
        fi
    done

    ls -hl /tmp | awk '{print $9}' | grep '[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}T[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}\.[0-9]\{3\}' | while read LINE
    do
        clean_log "/tmp/$LINE" $2
        if [ $use -lt $max_used ]; then
            return $use
        fi
    done

    if [ -z "`ls -hl $1|sed '1d'|sed '/.\.log/d'|sed '/.\.db/d'|sed '/.\.tmp/d'`" ]; then

        print "no folder remained, clear *.log" $2

        ls -hl $1|sed '1d'|sed '/.\.db/d'|sed '/txc.*\.\(.*\.\)*log\.1$/d'|sed '/txc.*\.\(.*\.\)*log$/d'| awk '{print $9}'|while read LINE
        do
            log_path="$1/$LINE"
            clean_log $log_path $2

            if [ $use -lt $max_used ]; then
                return $use
            fi
        done
    else

        skip_count=`expr 5 - $2 \* 2`
        if [ $skip_count -lt $[0] ]; then
            skip_count=0
        fi

        total_folder_count=`ls -lhtr $1|sed '1d'|sed '/.\.log/d'|sed '/.\.db/d'|wc -l`
        if [ $skip_count -gt $total_folder_count ]; then
            print "no folder removed in[$1] after skip [$skip_count]" $2
            return $use
        fi

        print "remove folders first" $2

        print "skip latest $skip_count folders" $2

        if [ $skip_count -gt $[0] ]; then
            ls -lhtr $1|sed '1d'|sed '/.\.log/d'|sed '/.\.db/d'|awk '{print $9}'|sort -r -t- -k1,1 -k2,2 -k3,3|sed "1, $skip_count d"|sort -r -t- -k1,1 -k2,2 -k3,3|while read LINE
            do
                log_path="$1/$LINE"
                clean_log $log_path $2
            done
        else
            ls -lhtr $1|sed '1d'|sed '/.\.log/d'|sed '/.\.db/d'|awk '{print $9}'|sort -t- -k1,1 -k2,2 -k3,3|while read LINE
            do
                log_path="$1/$LINE"
                clean_log $log_path $2
            done
        fi
    fi
}

clean_log() {
    clean_log_path=`echo $1|sed 's/ /\\\ /g'`

    if [ -d $clean_log_path ]; then
        print "dir[$clean_log_path] removed!" $2
        sudo rm -rvf $clean_log_path
    else
        if [[ ${clean_log_path: -6} == ".hprof" ]] || [[ "$clean_log_path" == *"gc.log-20"* ]] || [[ "$clean_log_path" == *"console.log-20"* ]] || [[ ${clean_log_path: -4} == ".tmp" ]]; then
            sudo rm -fv $clean_log_path
        elif [[ ${clean_log_path: -4} == ".pid" ]]; then
            print "skip the ${clean_log_path} file"
        else
            sudo cp /dev/null $clean_log_path
        fi
        print "file[$clean_log_path] cleared!" $2
    fi

    get_dir_size
    return $use
}



print "clean start! date[`date '+%Y/%m/%d %T'`]===================="

#强制删除log根目录下的*.log文件，历史原因导致有部分文件写到了根目录下面
ls -hl $cdc_log_path | awk '{print $9}' | while read LINE
do
    TEMP_FILE="$cdc_log_path/$LINE"
    if [ -f $TEMP_FILE ] && [[ "$LINE" == *".log" ]]; then
        print "file[$TEMP_FILE] removed!"
        sudo rm -fv $TEMP_FILE
    fi
done

# 0：删除5天以前的日志文件夹；1：删除3天以前的日志文件夹；2：删除1天前的日志文件夹；3：删除所有历史日志；4：删除所有日志
for clean_level in 0 1 2 3 4
do
    get_dir_size
    if [ $use -ge $max_used ];then
        print "path[$cdc_log_path] file system[$file_system] mount on[$mnt] used[$use%]"

        process_path="$cdc_log_path/polardbx-binlog"
        clean_process_log $process_path $clean_level

        process_path_2="$cdc_log_path/polardbx-rpl"
        clean_db_log $process_path_2 $clean_level

    else
        print "clean finish! current usage[$use GB] `date`===================="
        exit
    fi
done


if [ $use -ge $max_used ];then
    clean_log $cleaner_log
fi

#clean the big file
# shellcheck disable=SC2046
# shellcheck disable=SC2006
sudo rm -f `find /home/admin/ -type f -name "*.hprof" -exec ls -t {} + | awk 'NR > 3'` \;
sudo find /var/log/ -type f -size +500M -exec cp /dev/null {} \;

#use=`df -h $cdc_log_path|sed '1d'|awk '{print $5+0}'`
get_dir_size
print "clean finish! current useage $use GB `date`===================="


########################### binlog-rpl log 清理 #############################
# 删除超过 30 天未更新过的文件和文件夹
day=30
maxGb=30
folder=/home/admin/logs/polardbx-rpl

#  -path 排除 folder 本身
find $folder -path "$folder" -type d -mtime +$day -exec rm -rf {} \;
find $folder -type f -mtime +$day -exec rm -rf {} \;
# statistic.log, position.log, commit.log，gc.log 总是保存 30 天，因为每个文件大小比较固定，每天约 5M

while [ $day -ge 1 ]
do
day=`expr $day - 1`
rpl_log_size=`du -sb $folder | awk '{print $1}'`
rpl_log_size_g=`expr $rpl_log_size / 1024 / 1024 / 1024`
echo "rpl log size: " $rpl_log_size_g GB

if [ $rpl_log_size_g -ge $maxGb ];then
    # 如果还是很大，删除所有已经归档的 default.*.gz 和 meta.*.gz 文件
    echo remove $day day .gz log files
    find $folder -type f -mtime +$day -name 'default.*.gz' -exec ls -Shl {} +;
    find $folder -type f -mtime +$day -name 'meta.*.gz' -exec ls -Shl {} +;

    find $folder -type f -mtime +$day -name 'default.*.gz' -exec rm -rf {} \;
    find $folder -type f -mtime +$day -name 'meta.*.gz' -exec rm -rf {} \;
else
    echo break
    break
fi
done
