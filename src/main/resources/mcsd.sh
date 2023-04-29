#!/bin/bash
cd "$1" || echo "Could not change directory to $1"
while [ true ]; do
  java "-Xmx$2" -jar server.jar nogui
done
