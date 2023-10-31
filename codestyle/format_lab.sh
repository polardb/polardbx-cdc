#!/bin/sh

baseDir=`pwd`/
commit=`git merge-base origin/cdc_develop HEAD`
files=`git diff --name-only ${commit} | grep '.java' | grep -v 'polardbx-cdc-protocol' | grep -v 'polardbx-cdc-common/src/main/java/com/aliyun/polardbx/binlog/dao' | grep -v 'polardbx-cdc-common/src/main/java/com/aliyun/polardbx/binlog/domain/po' | xargs -I {} echo ${baseDir}{}`
count=0
batchFile=''
for file in $files; do
  if [ -f "$file" ]; then
	  count=$((($count + 1) % 100))
	  batchFile=$batchFile' '$file
	  if [[ $count -eq 0 ]]; then
		  /home/admin/idea/bin/format.sh -s ${baseDir}'codestyle/codestyle-idea.xml' -m '*.java' ${batchFile}
		  batchFile=''
	  fi
	  trap "echo Exited!; exit;" SIGINT SIGTERM
	fi
done

if [[ ! -z $batchFile ]]; then
	/home/admin/idea/bin/format.sh -s ${baseDir}'codestyle/codestyle-idea.xml' -m '*.java' ${batchFile}
fi
