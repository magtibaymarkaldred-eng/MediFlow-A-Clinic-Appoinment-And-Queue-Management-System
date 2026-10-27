package mediqueue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

final class SqliteStore {
    private static final String URL = "jdbc:sqlite:mediqueue.db";
    private SqliteStore() {}

    static void initialize() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");

            // Older versions may have tables created manually with different columns.
            // Preserve incompatible tables under a legacy name, then create the schema this app uses.
            if (tableExists(c, "users") && !hasColumn(c, "users", "password_hash")) {
                if (!tableExists(c, "users_legacy")) s.executeUpdate("ALTER TABLE users RENAME TO users_legacy");
                else s.executeUpdate("DROP TABLE users");
            }
            if (tableExists(c, "appointments") &&
                (!hasColumn(c, "appointments", "username") || !hasColumn(c, "appointments", "visit_type"))) {
                if (!tableExists(c, "appointments_legacy")) s.executeUpdate("ALTER TABLE appointments RENAME TO appointments_legacy");
                else s.executeUpdate("DROP TABLE appointments");
            }

            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY COLLATE NOCASE, password_hash TEXT NOT NULL, role TEXT NOT NULL, name TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS appointments (id TEXT PRIMARY KEY, username TEXT NOT NULL, patient TEXT NOT NULL, contact TEXT, doctor TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, visit_type TEXT, reason TEXT, status TEXT NOT NULL, created_at TEXT, FOREIGN KEY(username) REFERENCES users(username))");
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) seed(c);
            }
        } catch (SQLException e) { throw new IllegalStateException("Could not initialize SQLite database: " + e.getMessage(), e); }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            p.setString(1, table);
            try (ResultSet rs = p.executeQuery()) { return rs.next(); }
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            return false;
        }
    }
    private static Connection connect() throws SQLException { return DriverManager.getConnection(URL); }
    private static void seed(Connection c) throws SQLException {
        String sql = "INSERT INTO users(username,password_hash,role,name) VALUES(?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            addUser(p, "aldred", "aldred06", "admin", "Mark Aldred Magtibay");
            addUser(p, "tiffany", "tiffany012", "patient", "Tiffany");
            addUser(p, "aj", "aj32", "patient", "AJ");
            addUser(p, "stephen", "glovo456", "patient", "Stephen");
        }
    }
    private static void addUser(PreparedStatement p, String username, String password, String role, String name) throws SQLException {
        p.setString(1, username); p.setString(2, hash(password)); p.setString(3, role); p.setString(4, name); p.executeUpdate();
    }
    private static String hash(String value) {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(bytes); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static Map<String,Object> login(String username, String password) {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT username,role,name,password_hash FROM users WHERE username=? COLLATE NOCASE")) {
            p.setString(1, username.trim()); try (ResultSet rs = p.executeQuery()) {
                if (!rs.next() || !MessageDigest.isEqual(rs.getString("password_hash").getBytes(StandardCharsets.UTF_8), hash(password).getBytes(StandardCharsets.UTF_8))) return null;
                return user(rs.getString("username"), rs.getString("role"), rs.getString("name"));
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private static Map<String,Object> user(String username, String role, String name) {
        Map<String,Object> u = new LinkedHashMap<>(); u.put("username", username); u.put("role", role); u.put("name", name); return u;
    }
    static Map<String,Object> data() {
        Map<String,Object> out = new LinkedHashMap<>(); List<Object> users = new ArrayList<>(); List<Object> appointments = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT username,role,name FROM users ORDER BY username")) {
            while (rs.next()) users.add(user(rs.getString("username"), rs.getString("role"), rs.getString("name")));
        } catch (SQLException e) { throw new IllegalStateException(e); }
        String sql = "SELECT id,username,patient,contact,doctor,date,time,visit_type,reason,status,created_at FROM appointments ORDER BY date,time";
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) { Map<String,Object> a = new LinkedHashMap<>(); a.put("id",rs.getString("id")); a.put("username",rs.getString("username")); a.put("patient",rs.getString("patient")); a.put("contact",rs.getString("contact")); a.put("doctor",rs.getString("doctor")); a.put("date",rs.getString("date")); a.put("time",rs.getString("time")); a.put("visitType",rs.getString("visit_type")); a.put("reason",rs.getString("reason")); a.put("status",rs.getString("status")); a.put("createdAt",rs.getString("created_at")); appointments.add(a); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        out.put("users", users); out.put("appointments", appointments); return out;
    }
    static void replaceAppointments(List<?> rows) {
        String sql = "INSERT INTO appointments(id,username,patient,contact,doctor,date,time,visit_type,reason,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM appointments"); }
            try (PreparedStatement p = c.prepareStatement(sql)) {
                for (Object row : rows) {
                    if (!(row instanceof Map<?,?> a)) continue;
                    p.setString(1, val(a,"id")); p.setString(2,val(a,"username")); p.setString(3,val(a,"patient")); p.setString(4,val(a,"contact")); p.setString(5,val(a,"doctor")); p.setString(6,val(a,"date")); p.setString(7,val(a,"time")); p.setString(8,val(a,"visitType")); p.setString(9,val(a,"reason")); p.setString(10,val(a,"status")); p.setString(11,val(a,"createdAt")); p.addBatch();
                }
                p.executeBatch();
            }
            c.commit();
        } catch (SQLException e) { throw new IllegalStateException("Could not save appointments: " + e.getMessage(), e); }
    }
    private static String val(Map<?,?> m, String key) { Object v = m.get(key); return v == null ? "" : String.valueOf(v); }
}
