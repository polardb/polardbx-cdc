#!/bin/bash

K=$1
V=$2
pid=`ps -ef | grep  Daemon | grep -v grep | awk '{print $2}'`
port=`netstat -anpl | grep "$pid" |  grep LISTEN | awk '{print $4}' | awk -F ':' '{print $2}'`
HTML="\"http://127.0.0.1:${port}/config/v1/set\" -X POST -d \"{\"key\":\"${K}\", \"value\":\"${V}\"}\""
echo $HTML
curl "http://127.0.0.1:${port}/config/v1/set" -X POST -d "{\"key\":\"${K}\", \"value\":\"${V}\"}"
echo ""
curl "http://127.0.0.1:${port}/config/v1/get/${K}"
