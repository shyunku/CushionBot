#!/bin/sh
set -eu

JAR=build/libs/CushionBot-1.0-SNAPSHOT.jar
# outside build/, which "gradlew clean" deletes
PREV_JAR=.cushionbot-previous.jar

echo "============ BUILD START ============"

# Dashboard: build next to the live copy and swap it in, so the running bot never serves a half-built page.
(cd watcher && pnpm install --frozen-lockfile && pnpm exec tsc --noEmit -p tsconfig.json \
  && pnpm exec vite build --outDir dist-next --emptyOutDir)
rm -rf watcher/dist-prev
if [ -d watcher/dist ]; then mv watcher/dist watcher/dist-prev; fi
mv watcher/dist-next watcher/dist
rm -rf watcher/dist-prev

# Bot: keep the current jar so a failed build leaves the bot startable and running as it is.
if [ -f "$JAR" ]; then cp "$JAR" "$PREV_JAR"; fi
if ! ./gradlew clean build; then
  echo "Gradle build failed; restoring the previous jar and leaving the bot running." >&2
  if [ -f "$PREV_JAR" ]; then mkdir -p build/libs && cp "$PREV_JAR" "$JAR"; fi
  exit 1
fi

systemctl --user restart cushionbot.service
systemctl --user is-active --quiet cushionbot.service
echo "============ BUILD END ============"
