# MediQueue Java Web + SQLite

A beginner-friendly MediQueue web demo using Java JDK 17, the JDK built-in `HttpServer`, and a local SQLite database. It does not use Tomcat or a separate database server.

## Requirements
- JDK 17 or newer (`java -version` and `javac -version`)
- Windows PowerShell and an internet connection for the first build, so `build.bat` can download the SQLite JDBC driver
- A modern browser

## Run in VS Code
1. Extract the folder and open it in VS Code.
2. Choose **Terminal → New Terminal**.
3. Run `build.bat`. The first run downloads the SQLite JDBC driver into `lib/`.
4. Run `run.bat`.
5. Open `http://localhost:8080` in your browser.
6. Press Ctrl+C in the terminal to stop the server.

## Demo accounts
- Admin: `aldred` / `aldred06`
- Patient: `tiffany` / `tiffany012`
- Patient: `aj` / `aj32`
- Patient: `stephen` / `glovo456`

## SQLite database
The app creates `mediqueue.db` automatically in the project folder. It contains:
- `users` — usernames, SHA-256 password hashes, roles, and names
- `appointments` — appointment ID, patient, doctor, date, time, reason, and status

The browser signs in through the Java API. Appointment records are loaded from and saved to SQLite rather than browser `localStorage`. The `mediqueue.db` file is ignored by Git because it contains app data.

## Important notes
- SQLite JDBC is an external library; it is not included in the JDK. The build script downloads the driver once. After that, the project can build without downloading it again, as long as the `lib` folder is kept.
- This is a school/demo project, not production-ready medical software. Do not use real patient data. SHA-256 is used for this simple demo; production systems should use a password hashing algorithm designed for passwords (such as Argon2id or bcrypt), proper sessions, CSRF protection, and stronger authorization checks.
- `MediQueue.java` and `MediQueueData.java` remain included for the original desktop app. The web version uses `SqliteStore.java` for its database.
