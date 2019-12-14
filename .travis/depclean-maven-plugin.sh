#!/usr/bin/env bash

mvn clean install -Ptravis
mvn jacoco:report coveralls:report --fail-never
mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install sonar:sonar -Dsonar.projectKey=castor-software_depclean


