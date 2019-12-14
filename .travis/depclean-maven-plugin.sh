#!/usr/bin/env bash

mvn clean install -Ptravis
mvn jacoco:report coveralls:report --fail-never
mvn sonar:sonar
mvn clean verify sonar:sonar -Pcoverage