@echo off
if not exist build mkdir build
javac --release 17 -d build src\MediQueue.java src\MediQueueData.java
if errorlevel 1 exit /b 1
jar --create --file MediQueue.jar --main-class mediqueue.MediQueue -C build .
echo Build complete: MediQueue.jar
