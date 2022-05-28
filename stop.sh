#!/bin/sh
ps -ef | grep CushionBot | grep -v grep | awk '{print $2}' | xargs kill -9
echo "============ DAEMON STOPPED ============"