package com.judgeTool.util;

public final class StringUtil {

    private static final String ELLIPSIS = "…";

    private StringUtil() {
    }

    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", "\\n");
        return t.length() <= max ? t : t.substring(0, max) + ELLIPSIS;
    }
}
