# SQLite setup and tables

The web server creates `mediqueue.db` automatically when it starts. It creates two tables:

- `users(username, password_hash, role, name)`
- `appointments(id, username, patient, contact, doctor, date, time, visit_type, reason, status, created_at)`

The first start seeds four demo accounts. Passwords are stored as SHA-256 hashes, not plain text. The Java server exposes `/api/login`, `/api/data`, and `/api/appointments` for the web interface.

To reset the demo database, stop the server and delete `mediqueue.db`. The next start recreates the tables and demo accounts. **Deleting the file permanently deletes the appointments saved in that database.**

\n## If your database previously showed Tables (0) or used the wrong columns\n\n
The Java backend expects the `users` table to contain `username`, `password_hash`, `role`, and `name`.
The `appointments` table must contain `id`, `username`, `patient`, `contact`, `doctor`, `date`, `time`, `visit_type`, `reason`, `status`, and `created_at`.
When the app starts, the updated `SqliteStore` detects incompatible older tables and preserves them as `users_legacy` / `appointments_legacy` before creating the correct tables. Open the `mediqueue.db` file in this project folder, not another copy.\n
