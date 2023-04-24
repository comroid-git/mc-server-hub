#!/bin/bash
cd %1 || echo "Could not change directory to " + %1

while true; do
  java %1 -jar server.jar nogui
done
