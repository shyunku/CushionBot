#!/bin/sh
echo "============ BUILD START ============"
sh stop.sh
rm -rf build/libs
./gradlew clean build
sh run.sh
echo "============ BUILD END ============"