#!/bin/sh
set -e
mkdir -p build
javac --release 17 -d build src/MediQueue.java src/MediQueueData.java
jar --create --file MediQueue.jar --main-class mediqueue.MediQueue -C build .
echo "Build complete: MediQueue.jar"
