#!/bin/bash

defaultRam="4"
defaultBackupDir="$HOME/backup"

# run comamnd
if [ "$1" == "run" ]; then
  # fallback value for max ram
  if [ -z "$2" ]; then
    echo "No maximum RAM GB specified; falling back to $defaultRam"
    2="$defaultRam"
  fi

  # exec loop
  sock=".running"
  touch $sock
  first="";
  while [ -f $sock ]; do
    if [ -z "$first" ]; then
      first="no";
    else
      sleep "5s"
    fi
    java "-Xmx$2" -jar server.jar nogui
  done

  echo "Server was stopped"

# backup command
elif [ "$1" == "backup" ]; then
  # fallback value for backup directory
  if [ -z "$2" ]; then
    echo "No backup directory specified; falling back to $defaultBackupDir"
    2="$defaultBackupDir"
  fi

  # backup details
  now=$(date '+%Y_%m_%d_%H_%M')
  backup="$2/$now"

  # create backup
  echo "Compressing backup as $backup.tar.gz"
  tar -zcvf "$backup.tar.gz" ./*glob*

# invalid command
else
  echo "Invalid arguments"
fi
