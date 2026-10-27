package mediqueue;

import java.util.*;

final class Json {
    private Json() {}
    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return quote((String) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner join = new StringJoiner(",", "{", "}");
            map.forEach((k, v) -> join.add(quote(String.valueOf(k)) + ":" + write(v)));
            return join.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner join = new StringJoiner(",", "[", "]");
            for (Object item : items) join.add(write(item));
            return join.toString();
        }
        return quote(value.toString());
    }
    static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
