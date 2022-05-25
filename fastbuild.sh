echo "============ BUILD START ============"
rm -rf build/libs
./gradlew clean build
java -jar build/libs/CushionBot-1.0-SNAPSHOT.jar
echo "============ BUILD END ============"