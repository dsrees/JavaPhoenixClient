#!/bin/bash
# This script will build the project.
./gradlew clean publish --stacktrace
# if [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
#   echo -e '#### Build for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG'] ####'
# ./gradlew bintrayUpload --stacktrace
# else
#   echo -e '#### Build for Test => Branch ['$TRAVIS_BRANCH'] Pull Request ['$TRAVIS_PULL_REQUEST'] ####'
#   ./gradlew clean build jacocoTestReport
# fi 