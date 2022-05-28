#!/bin/sh
echo "============ DAEMON START ============"
nohup java -jar build/libs/CushionBot-1.0-SNAPSHOT.jar &
tail -f nohup.out