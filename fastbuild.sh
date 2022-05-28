#!/bin/sh
echo "============ BUILD START ============"
sh stop.sh
rm -rf build/libs
./gradlew clean build
sh start.sh
echo "============ BUILD END ============"