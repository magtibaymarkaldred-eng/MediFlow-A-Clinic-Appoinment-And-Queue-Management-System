# SQLite added without removing the existing project files

This folder was made by keeping the original MediQueue-Java-Web project and adding the SQLite-enabled Java backend files and frontend API changes.

Original files such as `src/MediQueue.java`, `src/MediQueueData.java`, the web UI files, and `docs/PHASE-BY-PHASE-GUIDE.txt` are retained. The web server and browser JavaScript were updated so the web demo can use SQLite for login and appointment storage.

Run `build.bat`, then `run.bat`, and open `http://localhost:8080`.
The SQLite JDBC driver is downloaded into `lib/` by the first build, and `mediqueue.db` is created when the server starts.
