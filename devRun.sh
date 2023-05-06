#!/bin/bash

switchDataDir() {
  echo "wip"
  #sudo rm "/srv/mcsd"
  #sudo ln -s "/srv/mcsd-$1" "/srv/mcsd"
}

killScreen() {
  if [ -n "$(screen -ls | grep -Po '\s\K[0-9]+')" ]; then
    kill -s SIGABRT "$(screen -ls | grep -Po '\s\K[0-9]+')"
    screen -wipe
  fi
}

variant="$1"
if [ -z "$variant" ];
 then variant="dev";
 else variant="prod";
fi

if [ "$variant" == "dev" ]; then
  # switch to dev variant
  killScreen
  git pull
  exec="gradle"
  if [ -z "$(which "$exec")" ]; then
    exec="gradlew"
  fi
  sudo systemctl stop mcsd-web
  switchDataDir $variant
  screen -DSR "mcsd-web-dev" $exec --no-daemon bootRun -Dorg.gradle.jvmargs="-Xdebug -XX:+HeapDumpOnOutOfMemoryError -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
else
  # switch to production variant
  killScreen
  sudo systemctl enable mcsd-web --now
fi
