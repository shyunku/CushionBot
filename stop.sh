#!/bin/sh
set -eu

systemctl --user stop cushionbot.service
echo "============ DAEMON STOPPED ============"
