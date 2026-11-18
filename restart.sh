#!/bin/sh
set -eu

systemctl --user restart cushionbot.service
systemctl --user is-active --quiet cushionbot.service
echo "============ DAEMON STARTED ============"
