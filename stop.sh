#!/bin/sh
ps -ef | grep CushionBot | grep -v grep | awk '{print $2}' | xargs kill -15
echo "============ DAEMON STOPPED ============"