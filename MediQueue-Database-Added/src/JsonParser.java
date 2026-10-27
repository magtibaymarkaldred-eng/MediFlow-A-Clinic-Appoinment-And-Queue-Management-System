package mediqueue;

import java.util.*;

final class JsonParser {
    private final String text;
    private int pos;
    private JsonParser(String text) { this.text = text; }
    static Object parse(String text) { JsonParser p = new JsonParser(text); Object v = p.value(); p.space(); if (p.pos != text.length()) throw new IllegalArgumentException("Unexpected JSON content"); return v; }
    private Object value() {
        space(); if (pos >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = text.charAt(pos);
        if (c == '"') return string();
        if (c == '{') return object();
        if (c == '[') return array();
        if (text.startsWith("true", pos)) { pos += 4; return true; }
        if (text.startsWith("false", pos)) { pos += 5; return false; }
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        int start = pos; while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw new IllegalArgumentException("Invalid JSON value");
        String n = text.substring(start, pos); return n.contains(".") || n.contains("e") || n.contains("E") ? Double.valueOf(n) : Long.valueOf(n);
    }
    private Map<String,Object> object() {
        Map<String,Object> map = new LinkedHashMap<>(); pos++; space(); if (take('}')) return map;
        do { space(); if (pos >= text.length() || text.charAt(pos) != '"') throw new IllegalArgumentException("Expected JSON key"); String key = string(); space(); require(':'); map.put(key, value()); space(); if (take('}')) return map; require(','); } while (true);
    }
    private List<Object> array() { List<Object> list = new ArrayList<>(); pos++; space(); if (take(']')) return list; do { list.add(value()); space(); if (take(']')) return list; require(','); } while (true); }
    private String string() {
        require('"'); StringBuilder out = new StringBuilder();
        while (pos < text.length()) { char c = text.charAt(pos++); if (c == '"') return out.toString(); if (c != '\\') { out.append(c); continue; }
            if (pos >= text.length()) throw new IllegalArgumentException("Bad JSON escape"); char e = text.charAt(pos++);
            switch (e) { case '"' -> out.append('"'); case '\\' -> out.append('\\'); case '/' -> out.append('/'); case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> { if (pos + 4 > text.length()) throw new IllegalArgumentException("Bad unicode escape"); out.append((char)Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; } default -> throw new IllegalArgumentException("Bad JSON escape"); }
        } throw new IllegalArgumentException("Unclosed JSON string");
    }
    private void space() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private boolean take(char c) { if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; } return false; }
    private void require(char c) { if (!take(c)) throw new IllegalArgumentException("Expected '" + c + "'"); }
}
