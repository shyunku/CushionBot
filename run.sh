#!/bin/sh
echo "============ DAEMON START ============"
nohup java -Xmx512m -jar build/libs/CushionBot-1.0-SNAPSHOT.jar &
tail -f nohup.out