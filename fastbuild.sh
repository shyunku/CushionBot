#!/bin/sh
echo "============ BUILD START ============"
sudo sh stop.sh
rm -rf build/libs
./gradlew clean build
sudo sh run.sh
echo "============ BUILD END ============"