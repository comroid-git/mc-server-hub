#!/bin/bash

git fetch
git pull
gradle bootRun -Dorg.gradle.jvmargs="-Xdebug -XX:+HeapDumpOnOutOfMemoryError -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"