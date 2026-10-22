# MediQueue - Clinic Appointment & Queue System

A beginner-friendly Java Swing desktop application. It uses the Java Development Kit (JDK) only and does not require a web server, Tomcat, Maven, or third-party libraries.

## Requirements
- JDK 17 or newer
- Visual Studio Code (optional; any Java-capable terminal works)

## Build
Open a terminal in this folder and run:

**Windows:**
```bat
build.bat
```

**macOS/Linux:**
```sh
chmod +x build.sh
./build.sh
```

Or compile manually:
```sh
javac --release 17 -d build src/MediQueue.java src/MediQueueData.java
```

## Run
After building:
```sh
java -jar MediQueue.jar
```

Or run directly from source with JDK 17:
```sh
java -cp build mediqueue.MediQueue
```

## Accounts
- Admin: username `aldred`, password `aldred06`
- Patient: username `tiffany`, password `tiffany012`
- Patient: username `aj`, password `aj32`
- Patient: username `stephen`, password `glovo456`

Keep the admin credentials private. These example credentials are stored in the application source, so this is a school/demo project rather than a production-ready authentication system.

## Local data
The program saves account and clinic data to local text files in the working directory. Back up these files before moving or deleting the project folder. Do not use real patient information in a demo without appropriate privacy and security safeguards.

## Main features
- Separate patient and admin login roles
- Patient registration and sign-in
- Appointment and queue management
- Doctor schedules
- Local file-based data storage

## Project contents
- `src/MediQueue.java` — window, login screens, and application interface
- `src/MediQueueData.java` — data models, accounts, and file storage
- `MediQueue.jar` — compiled runnable application
- `build/` — compiled Java class files
- `build.bat` / `build.sh` — build scripts
- `docs/` — project notes
