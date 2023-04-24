#!/bin/bash
cd "$1" || echo "Could not change directory to $1"
java "-Xmx$2" -jar server.jar nogui
