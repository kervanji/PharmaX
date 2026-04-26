package com.pharmax.update;

public final class AppVersion {
    private static final String FALLBACK = "1.2.4";

    private AppVersion() {
    }

    public static String current() {
        try {
            Package p = AppVersion.class.getPackage();
            if (p != null) {
                String v = p.getImplementationVersion();
                if (v != null && !v.trim().isEmpty()) {
                    return v.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return FALLBACK;
    }
}
