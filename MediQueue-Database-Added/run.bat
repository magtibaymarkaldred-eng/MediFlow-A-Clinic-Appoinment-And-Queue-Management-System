@echo off
cd /d "%~dp0"
if not exist "lib\sqlite-jdbc-3.53.4.0.jar" call build.bat
if errorlevel 1 pause & exit /b 1
if not exist build\mediqueue\MediQueueWeb.class call build.bat
if errorlevel 1 pause & exit /b 1
java -cp "build;lib\sqlite-jdbc-3.53.4.0.jar" mediqueue.MediQueueWeb %*
pause
