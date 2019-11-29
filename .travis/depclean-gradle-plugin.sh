#!/usr/bin/env bash

# install the core library
mvn clean install
# build the gradle plugin
cd depclean-gradle-plugin
./gradlew build