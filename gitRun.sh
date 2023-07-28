#!/bin/bash

#export DEBUG_ENV="yes"
export pidFile="unit.pid"
echo $$ > $pidFile
trap 'rm -f $pidFile' EXIT

function fetch() {
  git checkout main
  git pull
}

(
  cd '../japi';
  fetch
)

fetch

exec="gradle"
if [ -z "$(which "$exec")" ]; then
  exec="gradlew"
fi
$exec --no-daemon ':agent:bootRun';
