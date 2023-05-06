#!/bin/bash

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
  git pull
  exec="gradle"
  if [ -z "$(which "$exec")" ]; then
    exec="gradlew"
  fi
  sudo systemctl stop mcsd-web
  switchDataDir $variant
  $exec --no-daemon bootRun -Dorg.gradle.jvmargs="-Xdebug -XX:+HeapDumpOnOutOfMemoryError -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
else
  # switch to production variant
  sudo systemctl enable mcsd-web --now
fi
