#!/bin/bash

TAOBAO_JAVA="/opt/taobao/java/bin/jps"
ALIBABA_JAVA="/usr/alibaba/java/bin/jps"
if [ -f $TAOBAO_JAVA ] ; then
        JAVA=$TAOBAO_JAVA
elif [ -f $ALIBABA_JAVA ] ; then
        JAVA=$ALIBABA_JAVA
else
        JAVA=$(which jps)
        if [ ! -f $TAOBAO_JAVA ]; then
                echo "Cannot find a Java JDK. Please set either set JAVA or put java (>=1.5) in your PATH." 2>&2
                exit 1
        fi
fi


#####################################
checkpage() {
Java=$1
process=`$Java | grep  TddlLauncher`

echo  `$Java`

if [ "$process" == "" ];then
        echo "check failed"
        status=0
        error=1
else
        echo "check success"
        status=1
        error=0
fi
echo
  return $error
}

checkpage $JAVA
Footer
