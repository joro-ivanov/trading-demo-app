package com.example.demo.e2e.framework.ui;

/** Thin static facade over the browser's sessionStorage for the current Playwright page. */
public final class SessionStorage {

    private SessionStorage() {
    }

    public static String getItem(String key) {
        return evaluate("key => sessionStorage.getItem(key)", key);
    }

    public static void updateItem(String key, String value) {
        evaluate("([key, value]) => { sessionStorage.setItem(key, value); return null; }", key, value);
    }

    public static void removeItem(String key) {
        evaluate("key => { sessionStorage.removeItem(key); return null; }", key);
    }

    public static void removeAll() {
        PlaywrightManager.page().evaluate("() => { sessionStorage.clear(); return null; }");
    }

    private static String evaluate(String script, Object arg) {
        return (String) PlaywrightManager.page().evaluate(script, arg);
    }

    private static void evaluate(String script, String key, String value) {
        PlaywrightManager.page().evaluate(script, new String[]{key, value});
    }
}
