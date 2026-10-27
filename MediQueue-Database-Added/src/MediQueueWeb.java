package mediqueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** JDK HTTP server for MediQueue; clinic records are stored in SQLite. */
public class MediQueueWeb {
    private static final Map<String, String> TYPES = Map.of(
        "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8"
    );

    public static void main(String[] args) throws IOException {
        int port = 8080;
        if (args.length > 0) { try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) { System.out.println("Using default port 8080."); } }
        try { Class.forName("org.sqlite.JDBC"); SqliteStore.initialize(); }
        catch (Exception e) { System.err.println("SQLite could not start. Check that lib/sqlite-jdbc-3.53.4.0.jar exists."); e.printStackTrace(); return; }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", MediQueueWeb::handleApi);
        server.createContext("/", MediQueueWeb::serveFile);
        server.start();
        System.out.println("MediQueue is running at http://localhost:" + port);
        System.out.println("SQLite database: mediqueue.db");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/data") && method.equals("GET")) {
                send(exchange, 200, "application/json; charset=utf-8", Json.write(SqliteStore.data()).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/api/login") && method.equals("POST")) {
                Object parsed = JsonParser.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?,?> body)) { sendJson(exchange, 400, Map.of("error", "Invalid request.")); return; }
                Object usernameValue = body.get("username");
                Object passwordValue = body.get("password");
                String username = usernameValue == null ? "" : String.valueOf(usernameValue);
                String password = passwordValue == null ? "" : String.valueOf(passwordValue);
                Map<String,Object> user = SqliteStore.login(username, password);
                if (user == null) sendJson(exchange, 401, Map.of("error", "Incorrect username or password. Please try again."));
                else sendJson(exchange, 200, Map.of("ok", true, "user", user));
                return;
            }
            if (path.equals("/api/appointments") && method.equals("PUT")) {
                Object parsed = JsonParser.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (!(parsed instanceof List<?> rows)) { sendJson(exchange, 400, Map.of("error", "Expected an appointment list.")); return; }
                SqliteStore.replaceAppointments(rows);
                sendJson(exchange, 200, Map.of("ok", true));
                return;
            }
            sendJson(exchange, 404, Map.of("error", "API endpoint not found."));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", "Invalid JSON request."));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "The server could not complete the request."));
        } finally { exchange.close(); }
    }

    private static void serveFile(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) { send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8)); return; }
            String path = exchange.getRequestURI().getPath();
            String name = path.equals("/") ? "index.html" : path.substring(1);
            if (!name.matches("[A-Za-z0-9_-]+\\.(html|css|js)")) { send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
            Path file = Paths.get("web", name).normalize();
            if (!Files.isRegularFile(file)) { send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
            String ext = name.substring(name.lastIndexOf('.') + 1);
            send(exchange, 200, TYPES.get(ext), Files.readAllBytes(file));
        } finally { exchange.close(); }
    }
    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException { send(exchange, status, "application/json; charset=utf-8", Json.write(value).getBytes(StandardCharsets.UTF_8)); }
    private static void send(HttpExchange exchange, int status, String type, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
    }
}
