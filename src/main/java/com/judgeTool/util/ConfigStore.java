package com.judgeTool.util;

import java.util.prefs.Preferences;

public final class ConfigStore {
    private static final String NODE = "com.judgeTool";
    private static final String KEY_GEMINI = "gemini_api_key";

    private ConfigStore() {
    }

    public static String getGeminiApiKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String pref = Preferences.userRoot().node(NODE).get(KEY_GEMINI, "").trim();
        if (pref.isBlank()) {
            return "AIzaSyBeXYCgN6Rj3tWDzZQG9sv16fA_RhSh4N0";
        }
        return pref;
    }

    public static void setGeminiApiKey(String key) {
        Preferences.userRoot().node(NODE).put(KEY_GEMINI, key == null ? "" : key.trim());
    }
}
