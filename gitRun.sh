#!/bin/bash

export pidFile="unit.pid"
echo $$ > $pidFile
trap 'rm -f $pidFile' EXIT

git checkout main
git pull

exec="gradle"
if [ -z "$(which "$exec")" ]; then
  exec="gradlew"
fi
$exec --no-daemon ':agent:bootRun';
