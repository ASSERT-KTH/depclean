clear
mvn clean install
cd example || exit
./gradlew depclean --stacktrace
