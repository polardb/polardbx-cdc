#!/bin/sh

baseDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"/../
mergeBase=`git merge-base origin/cdc_develop HEAD`
files=`git diff --name-only ${mergeBase} | grep '.java' |  grep -v 'polardbx-cdc-protocol' | grep -v 'polardbx-cdc-common/src/main/java/com/aliyun/polardbx/binlog/dao' | grep -v 'polardbx-cdc-common/src/main/java/com/aliyun/polardbx/binlog/domain/po' | xargs -I {} echo ${baseDir}{}`
count=0
batchFile=''
for file in $files; do
    if [ -f "$file" ]; then
        count=$((($count + 1) % 100))
        batchFile=$batchFile' '$file
        if [[ $count -eq 0 ]]; then
            /Applications/IntelliJ\ IDEA\ CE.app/Contents/MacOS/idea format -s ${baseDir}'codestyle/codestyle-idea.xml' -m '*.java' ${batchFile}
            batchFile=''
        fi
        trap "echo Exited!; exit;" SIGINT SIGTERM
    fi
done

if [[ ! -z $batchFile ]]; then
	/Applications/IntelliJ\ IDEA\ CE.app/Contents/MacOS/idea format -s ${baseDir}'codestyle/codestyle-idea.xml' -m '*.java' ${batchFile}
fi


