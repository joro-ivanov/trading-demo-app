package com.example.demo.e2e.framework.ui;

/** Thin static facade over the browser's localStorage for the current Playwright page. */
public final class LocalStorage {

    private LocalStorage() {
    }

    public static String getItem(String key) {
        return evaluate("key => localStorage.getItem(key)", key);
    }

    public static void updateItem(String key, String value) {
        evaluate("([key, value]) => { localStorage.setItem(key, value); return null; }", key, value);
    }

    public static void removeItem(String key) {
        evaluate("key => { localStorage.removeItem(key); return null; }", key);
    }

    public static void removeAll() {
        PlaywrightManager.page().evaluate("() => { localStorage.clear(); return null; }");
    }

    private static String evaluate(String script, Object arg) {
        return (String) PlaywrightManager.page().evaluate(script, arg);
    }

    private static void evaluate(String script, String key, String value) {
        PlaywrightManager.page().evaluate(script, new String[]{key, value});
    }
}
