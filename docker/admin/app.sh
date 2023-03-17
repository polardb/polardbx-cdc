#!/bin/bash

source /etc/profile

function start_app() {
    chmod 755 /home/admin/polardbx-binlog.standalone/bin/*
    sudo /usr/sbin/crond -i
    sudo -E su admin -c 'sh /home/admin/polardbx-binlog.standalone/bin/daemon.sh start'
}

function stop_app() {
    sudo -E su admin -c 'sh /home/admin/polardbx-binlog.standalone/bin/daemon.sh stop'
}

function config_env() {
    if [ ! -d "/home/admin/bin" ]; then
        mkdir -p /home/admin/bin
    fi
    rm -f /home/admin/bin/env.sh
    touch /home/admin/bin/env.sh
    declare -xp > /home/admin/bin/env.sh
    sed -i '/declare -x HOME/d' /home/admin/bin/env.sh
    sed -i '/declare -x LOGNAME/d' /home/admin/bin/env.sh
    sed -i '/declare -x MAIL/d' /home/admin/bin/env.sh
    sed -i '/declare -x USER/d' /home/admin/bin/env.sh
    if [ ! -d "/home/admin/env" ]; then
        mkdir -p /home/admin/env
    fi

    if [ ! -f "/home/admin/env/env.properties" ]; then
        touch /home/admin/env/env.properties
        echo "ins_ip=$(hostname -i)" >> /home/admin/env/env.properties
    fi
    chown -R admin:admin /home/admin/.
}

config_env
start_app
source /home/admin/proc.sh
waitterm
stop_app
