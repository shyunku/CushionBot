#!/bin/sh
set -eu

echo "============ DAEMON START ============"
systemctl --user start cushionbot.service
systemctl --user is-active --quiet cushionbot.service
