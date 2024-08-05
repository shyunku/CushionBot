#!/bin/sh
ps -ef | grep CushionBot | grep -v grep | awk '{print $2}' | xargs kill -9
echo "============ DAEMON STOPPED ============"
nohup java -jar build/libs/CushionBot-1.0-SNAPSHOT.jar &
tail -f nohup.out
echo "============ DAEMON STARTED ============"