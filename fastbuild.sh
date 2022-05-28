#!/bin/sh
echo "============ BUILD START ============"
sudo sh stop.sh
rm -rf build/libs
./gradlew clean build
sudo sh start.sh
echo "============ BUILD END ============"