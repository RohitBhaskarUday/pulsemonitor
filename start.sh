#!/bin/bash

# Kill whatever is using port 8888
echo "Killing process on port 8888 if exists..."
PID=$(lsof -ti tcp:8888)

if [ -n "$PID" ]; then
  echo "Found process on port 8888: PID=$PID. Killing..."
  kill -9 $PID
else
  echo "No process found on port 8888."
fi

# Rebuild only if necessary
echo "Building project (only compile, not clean install)..."
mvn compile

# Run the app
echo "Starting application..."
mvn exec:java
