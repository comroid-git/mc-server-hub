#!/bin/bash

unitFile="mcsd-unit.properties"

# load unit data
if [ -f "$unitFile" ]; then
  echo "Loading Unit Information (wip)"

  while IFS='=' read -r key value; do
    # skip comments
    isComment=$(echo "$key" | grep -Po '#\K.+')
    if [ -n "$isComment" ]; then
      continue
    fi

    # strip double-quote characters
    stripQuotes=$(echo "$value" | grep -Po '"\K.+(?=")')
    if [ -n "$stripQuotes" ]; then
      value="$stripQuotes"
    fi

    # strip leading & trailing newlines
    value="$(echo "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | tr -d '\n')"

    echo "Loaded variable [$key] = [$value]"
    export "$key"="$value"
  done <mcsd-unit.properties
fi

# start command
if [ "$1" == "start" ]; then
  scrLs=$(screen -ls | grep "$unitName")
  if [ -z "$scrLs" ]; then
    screen -OdmSq "$unitName" ./mcsd.sh run || echo "Could not start screen session"
  else
    echo "Server $unitName did not need to be started"
  fi

# attach command
elif [ "$1" == "attach" ]; then
  screen -ODSRq "$unitName" ./mcsd.sh run || echo "Could not start screen session"

# run comamnd
elif [ "$1" == "run" ]; then
  # exec loop
  sock=".running"
  touch $sock
  first=""
  while [ -f $sock ]; do
    if [ -z "$first" ]; then
      first="no"
    else
      echo "Restarting Server..."
      sleep "5s"
    fi
    java "-Xmx${ramGB}G" -jar server.jar nogui
  done

  echo "Server was stopped"

# backup command
elif [ "$1" == "backup" ]; then
  # backup details
  now=$(date '+%Y_%m_%d_%H_%M')
  backup="$backupDir/$now"

  # create backup
  echo "Compressing backup as $backup.tar.gz"
  tar -zcvf "$backup.tar.gz" ./*glob*

# install dependencies command
elif [ "$1" == "installDeps" ]; then
  if [ -f "$(which pacman)" ]; then
    sudo pacman -Sy screen tar grep coreutils sed wget
  else
    # use apt-get
    sudo apt-get update && (sudo apt-get install screen tar grep coreutils sed wget || (echo "Uh-Oh, looks like that was wrong" && return 1))
    # todo: some packages might be wrong
  fi

# install command
elif [ "$1" == "install" ] || [ "$1" == "update" ]; then
  if [ "$1" == "install" ]; then
    while [ -z "$unitName" ]; do
      echo "Enter a unit name:"
      read -r unitName
    done

    if [ -z "$backupDir" ]; then
      echo "Enter a backup directory (~/backup):"
      read -r backupDir
    fi
    if [ -z "$backupDir" ]; then
      backupDir="$HOME/backup"
    fi

    if [ -z "$ramGB" ]; then
      echo "Enter the maximum RAM amount in GB (4):"
      read -r ramGB
    fi
    if [ -z "$ramGB" ]; then
      ramGB="4"
    fi

    agree="unset"
    while [ "$agree" != "yes" ]; do
      if [ "$agree" != "unset" ]; then
        echo "Please type 'yes' or 'no'"
      fi
      echo "Do you agree with Minecraft's EULA? (https://www.minecraft.net/eula) [yes/no]:"
      read -r agree
      if [ "$agree" == "no" ]; then
        echo "Goodbye"
        return
      fi
    done
    echo "eula=true" >"eula.txt"

    if [ -z "$mode" ]; then
      mode="unset"
      while [ $mode != "vanilla" ] && [ $mode != "paper" ] && [ $mode != "forge" ] && [ $mode != "fabric" ]; do
        echo "Please select a mode [vanilla/paper/forge/fabric]:"
        read -r mode
      done
    fi

    if [ -z "$mcVersion" ]; then
      mcVersion="unset"
      while [ -z "$mcVersion" ] || [ $mcVersion == "unset" ]; do
        echo "Please select a version:"
        read -r mcVersion
      done
    fi

    if [ -f "$unitFile" ]; then
      echo "Not overwriting unit data"
    else
      # write collected data to unit file
      echo "unitName=$unitName" >"$unitFile"
      echo "backupDir=$backupDir" >"$unitFile"
      echo "ramGB=$ramGB" >"$unitFile"
      echo "mode=$mode" >"$unitFile"
      echo "mcVersion=$mcVersion" >>"$unitFile"
    fi
  fi

  echo "Downloading server.jar..."
  mode=$(echo "$mode" | tr '[:upper:]' '[:lower:]')
  if [ "$mode" == "paper" ]; then
    type="servers"
  elif [ "$mode" == "forge" ]; then
    type="modded"
  elif [ "$mode" == "fabric" ]; then
    type="modded"
  else
    type="vanilla"
  fi
  wget -O server.jar "https://serverjars.com/api/fetchJar/$type/$mode/$mcVersion"
  chmod 755 server.jar

# invalid command
else
  echo "Invalid command"
fi
