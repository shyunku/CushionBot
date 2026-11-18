#!/bin/sh
set -eu

echo "============ BUILD START ============"
(cd watcher && pnpm install --frozen-lockfile && pnpm build)
./gradlew clean build
systemctl --user restart cushionbot.service
systemctl --user is-active --quiet cushionbot.service
echo "============ BUILD END ============"
