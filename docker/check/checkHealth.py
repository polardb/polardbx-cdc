#-*- coding:utf-8 -*-
import httplib
import os
import sys
import commands


#本机的简单测试

#work around , fix later
port='3306'
#port=os.environ['mc_http_port']

if port is None or port=='':
    print "port not provided"
    sys.exit(2)
# just ping 
status,output = commands.getstatusoutput("MYSQL_BIN=$(which mysql);$MYSQL_BIN -h127.0.0.1 -P%s -e 'set names utf8'" % port)
if status != 0 or 'ERROR' in output:
    print "Service down."
    sys.exit(1)

print "Service is up !"
sys.exit(0)
