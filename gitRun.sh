#!/bin/bash

#export DEBUG_ENV="yes"
export pidFile="unit.pid"
echo $$ > $pidFile
trap 'rm -f $pidFile' EXIT

if [ -z $1 ]; then
  branch="main"
else
  branch="$1"
fi

function fetch() {
  prevBranch="$branch"
  if [ "$(git show-ref --verify --quiet "refs/heads/$branch")" ]; then
    branch="main"
  fi
  git checkout "$branch"
  git pull
  branch="$prevBranch"
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
