#!/bin/bash

#export DEBUG_ENV="yes"
export pidFile="unit.pid"
echo $$ > $pidFile
trap 'rm -f $pidFile' EXIT

if [ -z $2 ]; then
  branch="main"
else
  branch="$2"
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
debugOptions=""
if [ -z "$(which "$exec")" ]; then
  exec="gradlew"
fi
if [ $branch != "main" ]; then
  debugOptions="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

$exec --no-daemon ":$1:simplify";
java -Xmx2G $debugOptions -jar agent/build/libs/agent.jar;
