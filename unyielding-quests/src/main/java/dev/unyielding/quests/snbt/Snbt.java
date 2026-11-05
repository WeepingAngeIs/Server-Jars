package dev.unyielding.quests.snbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, lenient parser for the SNBT-like text format used by quest data files
 * (commas between entries are optional, '#' starts a comment line).
 * Result types: Map&lt;String,Object&gt;, List&lt;Object&gt;, String, Boolean, Long, Double.
 */
public final class Snbt {
    private final String t;
    private int i;

    private Snbt(String text) {
        this.t = text;
    }

    public static Object parse(String text) {
        Snbt p = new Snbt(text);
        p.ws();
        Object v = p.value();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String text) {
        Object o = parse(text);
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Top level value is not a compound");
    }

    private void ws() {
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == ',' || c == '﻿') {
                i++;
            } else if (c == '#') {
                while (i < t.length() && t.charAt(i) != '\n') i++;
            } else {
                break;
            }
        }
    }

    private IllegalArgumentException err(String msg) {
        int line = 1;
        for (int k = 0; k < Math.min(i, t.length()); k++) if (t.charAt(k) == '\n') line++;
        return new IllegalArgumentException(msg + " (line " + line + ")");
    }

    private Object value() {
        ws();
        if (i >= t.length()) throw err("Unexpected end of input");
        char c = t.charAt(i);
        if (c == '{') {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            while (true) {
                ws();
                if (i >= t.length()) throw err("Unterminated compound");
                if (t.charAt(i) == '}') {
                    i++;
                    return m;
                }
                String k = key();
                ws();
                if (i >= t.length() || t.charAt(i) != ':') throw err("Expected ':' after key " + k);
                i++;
                m.put(k, value());
            }
        }
        if (c == '[') {
            i++;
            List<Object> l = new ArrayList<>();
            // typed array prefix like [I; ...]
            if (i + 1 < t.length() && (t.charAt(i) == 'I' || t.charAt(i) == 'B' || t.charAt(i) == 'L') && t.charAt(i + 1) == ';') {
                i += 2;
            }
            while (true) {
                ws();
                if (i >= t.length()) throw err("Unterminated list");
                if (t.charAt(i) == ']') {
                    i++;
                    return l;
                }
                l.add(value());
            }
        }
        if (c == '"' || c == '\'') {
            return quoted();
        }
        int s = i;
        while (i < t.length()) {
            char d = t.charAt(i);
            if (d == ' ' || d == '\t' || d == '\r' || d == '\n' || d == ',' || d == ']' || d == '}' || d == ':') break;
            i++;
        }
        if (s == i) throw err("Unexpected character '" + c + "'");
        return scalar(t.substring(s, i));
    }

    private String key() {
        ws();
        if (i < t.length() && (t.charAt(i) == '"' || t.charAt(i) == '\'')) return quoted();
        int s = i;
        while (i < t.length()) {
            char d = t.charAt(i);
            if (d == ':' || d == ' ' || d == '\t' || d == '\r' || d == '\n' || d == '{' || d == '}' || d == '[' || d == ']' || d == ',') break;
            i++;
        }
        if (s == i) throw err("Expected key");
        return t.substring(s, i);
    }

    private String quoted() {
        char q = t.charAt(i++);
        StringBuilder sb = new StringBuilder();
        while (i < t.length() && t.charAt(i) != q) {
            char c = t.charAt(i);
            if (c == '\\' && i + 1 < t.length()) {
                i++;
                char e = t.charAt(i);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (i + 4 < t.length()) {
                            try {
                                sb.append((char) Integer.parseInt(t.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                sb.append('u');
                            }
                        }
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        if (i >= t.length()) throw err("Unterminated string");
        i++;
        return sb.toString();
    }

    private static Object scalar(String w) {
        if (w.equals("true")) return Boolean.TRUE;
        if (w.equals("false")) return Boolean.FALSE;
        int n = w.length();
        char last = w.charAt(n - 1);
        String body = w;
        char suffix = 0;
        if ("bBsSlLfFdD".indexOf(last) >= 0 && n > 1) {
            body = w.substring(0, n - 1);
            suffix = Character.toLowerCase(last);
        }
        try {
            if (suffix == 'f' || suffix == 'd' || body.contains(".") || body.contains("e") || body.contains("E")) {
                return Double.parseDouble(body);
            }
            return Long.parseLong(body);
        } catch (NumberFormatException e) {
            return w;
        }
    }

    // ---- typed accessors -------------------------------------------------

    public static String str(Map<String, Object> m, String k, String def) {
        Object o = m.get(k);
        return o == null ? def : String.valueOf(o);
    }

    public static long lng(Map<String, Object> m, String k, long def) {
        Object o = m.get(k);
        if (o instanceof Long l) return l;
        if (o instanceof Double d) return (long) (double) d;
        if (o instanceof Boolean b) return b ? 1 : 0;
        return def;
    }

    public static double dbl(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        if (o instanceof Double d) return d;
        if (o instanceof Long l) return l;
        return def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object o = m.get(k);
        if (o instanceof Boolean b) return b;
        if (o instanceof Long l) return l != 0;
        if (o instanceof String s) return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("enabled");
        return def;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (o instanceof List<?> l) return (List<Object>) l;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (o instanceof Map<?, ?> mm) return (Map<String, Object>) mm;
        return null;
    }
}
