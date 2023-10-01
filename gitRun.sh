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
  if [ -f "force_commit.txt" ]; then
    echo "Checking out commit: $(cat "force_commit.txt")"
    git checkout "$(cat "force_commit.txt")"
    return
  fi
  prevBranch="$branch"
  if [ "$(git show-ref --verify --quiet "refs/heads/$branch")" ]; then
    branch="main"
    echo "Checking out default branch: $branch"
    git checkout "$branch"
  fi
  echo "Pulling changes from $branch..."
  git pull

  branch="$prevBranch"
  echo "Restoring branch: $branch"
}

(
  cd '../japi' || exit;
  fetch
)

fetch

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
java --version
java -Xmx2G "$debugOptions" -jar "$jarfile";
