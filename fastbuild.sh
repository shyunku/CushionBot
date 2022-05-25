echo "============ BUILD START ============"
rm build/libs/CushionBot-1.0-SNAPSHOT.jar
./gradlew clean build
java -jar build/libs/CushionBot-1.0-SNAPSHOT.jar
echo "============ BUILD END ============"