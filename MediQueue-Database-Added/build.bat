@echo off
cd /d "%~dp0"
if not exist lib mkdir lib
if not exist "lib\sqlite-jdbc-3.53.4.0.jar" (
  echo Downloading SQLite JDBC driver (required for SQLite support)...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar' -OutFile 'lib/sqlite-jdbc-3.53.4.0.jar'"
  if errorlevel 1 (
    echo Download failed. Check your internet connection and try again.
    exit /b 1
  )
)
if exist build rmdir /s /q build
mkdir build
javac --release 17 -encoding UTF-8 -cp "lib\sqlite-jdbc-3.53.4.0.jar" -d build src\MediQueueData.java src\MediQueue.java src\Json.java src\JsonParser.java src\SqliteStore.java src\MediQueueWeb.java
if errorlevel 1 exit /b 1
echo Build complete. Run run.bat, then open http://localhost:8080
