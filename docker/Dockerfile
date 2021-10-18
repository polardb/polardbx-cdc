FROM centos:7

# Install essential utils
RUN yum update -y && \
    yum install sudo hostname telnet net-tools vim tree less file java-1.8.0-openjdk -y && \
    yum clean all && rm -rf /var/cache/yum && rm -rf /var/tmp/yum-*

# Create user "admin" and add it into sudo group
RUN useradd -ms /bin/bash admin && \
    echo "admin:admin" | chpasswd && \
    echo "admin    ALL=(ALL)    NOPASSWD: ALL" >> /etc/sudoers

WORKDIR /home/admin

COPY admin /home/admin/
COPY polardbx-binlog.tar.gz  /home/admin/polardbx-binlog.tar.gz

RUN chmod +x /home/admin/app.sh

ADD check/checkHealth.py /
COPY check/health.sh /home/admin/
ADD etc/crontab.log_cleaner /etc/cron.d/crontab.log_cleaner
ADD etc/crontab.process_check /etc/cron.d/crontab.process_check

RUN mkdir -p /home/admin/.bin
ADD bin/cdc_log_cleaner.sh /home/admin/.bin/cdc_log_cleaner.sh
RUN chmod +x /home/admin/.bin/cdc_log_cleaner.sh && \
    tar -xzvf /home/admin/polardbx-binlog.tar.gz && \
    chmod 755 /home/admin/health.sh && \
    chmod 644 /etc/cron.d/crontab.log_cleaner && \
    chmod 644 /etc/cron.d/crontab.process_check && \
    chown -R admin:admin /home/admin && \
    chgrp -R admin /home/admin/ && \
    chmod 755 /home/admin/polardbx-binlog.standalone/bin/*.sh && \
    rm /home/admin/polardbx-binlog.tar.gz && \
    rm -f /etc/localtime && \
    true

CMD /home/admin/app.sh