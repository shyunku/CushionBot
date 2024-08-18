#!/bin/sh
sh stop.sh
echo "============ DAEMON STOPPED ============"
nohup java -jar build/libs/CushionBot-1.0-SNAPSHOT.jar &
tail -f nohup.out
echo "============ DAEMON STARTED ============"