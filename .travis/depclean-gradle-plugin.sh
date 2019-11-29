#!/usr/bin/env bash

cd depclean-core
mvn clean install

cd ..

cd depclean-gradle-plugin
./gradlew build