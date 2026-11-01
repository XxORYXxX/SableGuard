package com.mojang.logging;
public class LogUtils {
    public static final java.util.List<String> LINES = new java.util.ArrayList<>();
    static String fmt(String level, String m, Object[] a) {
        StringBuilder b = new StringBuilder(level).append(' ');
        int i = 0, from = 0, at;
        while ((at = m.indexOf("{}", from)) >= 0) { b.append(m, from, at).append(i < a.length ? a[i++] : "{}"); from = at + 2; }
        return b.append(m.substring(from)).toString();
    }
    public static org.slf4j.Logger getLogger() {
        return new org.slf4j.Logger() {
            public void info(String m, Object... a) { LINES.add(fmt("INFO", m, a)); }
            public void warn(String m, Object... a) { LINES.add(fmt("WARN", m, a)); }
            public void error(String m, Object... a) { LINES.add(fmt("ERROR", m, a)); }
        };
    }
}
