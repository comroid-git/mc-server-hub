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
  if [ -n "$1" ]; then
    echo "Checking out commit: $1"
    git checkout "$1"
  elif [ "$(git show-ref --verify --quiet "refs/heads/$branch")" ]; then
    branch="main"
    echo "Checking out default branch: $branch"
    git checkout "$branch"
  fi

  echo "Pulling changes..."
  git pull
  branch="$prevBranch"
  echo "Restoring branch: $branch"
}

(
  cd '../japi' || exit;
  fetch
)

if [ -f "force_commit.txt" ]; then
  fetch "$(cat "force_commit.txt")"
else
  fetch
fi

exec="gradle"
if [ -z "$(which "$exec")" ]; then
  exec="gradlew"
fi
if [ "$branch" != "main" ]; then
  debugOptions="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

$exec --no-daemon ":$1:simplify";
jarfile="$1/build/libs/$1.jar"
echo "Executing $jarfile"
java -Xmx2G "$debugOptions" -jar "$jarfile";
