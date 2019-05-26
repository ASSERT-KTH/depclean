#!/usr/bin/env bash

cd jdbl-pom-maven-plugin

echo "----- INSTALLING THE jdbl-pom PLUGIN -----"
mvn clean
mvn -q install

echo "----- RUNNING THE EXPERIMENTS ON THE dummy-project -----"
cd experiments/dummy-project
mvn clean package

#echo "----- RUNNING THE EXPERIMENTS ON jcabi -----"
#cd ..
#cd experiments/dummy-project
#mvn clean package
#
#echo "----- RUNNING THE EXPERIMENTS ON jcabi -----"
#cd ..
#cd experiments/dummy-project
#mvn clean pac

