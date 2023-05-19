#!/bin/bash

export DEBUG="true"

export pidFile="unit.pid"
echo $$ > $pidFile
trap 'rm -f $pidFile' EXIT

switchDataDir() {
  echo "wip"
  #sudo rm "/srv/mcsd"
  #sudo ln -s "/srv/mcsd-$1" "/srv/mcsd"
}

variant="$1"
if [ -z "$variant" ];
 then variant="dev";
 else variant="prod";
fi

if [ "$variant" == "dev" ]; then
  # switch to dev variant
  git checkout dev
  git pull
  exec="gradle"
  if [ -z "$(which "$exec")" ]; then
    exec="gradlew"
  fi
  sudo systemctl stop mcsd-web
  switchDataDir $variant
  if [ -f ".slave" ]; then
    debugArgs="-agentlib:jdwp=transport=dt_socket,server=n,address=dev.kaleidox.de:5005,suspend=y,onuncaught=y"
  else debugArgs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"; fi
  $exec --no-daemon simplifyDist
  java -Xdebug -XX:+HeapDumpOnOutOfMemoryError $debugArgs -jar build/dist/mc-server-hub.war
else
  # switch to production variant
  sudo systemctl enable mcsd-web --now
fi
