package com.pharmax.update;

import java.util.ArrayList;
import java.util.List;

public final class VersionUtil {
    private VersionUtil() {
    }

    public static String normalizeTagToVersion(String tag) {
        if (tag == null) {
            return "";
        }
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
        }
        return t.trim();
    }

    public static int compareVersions(String a, String b) {
        List<Integer> pa = parseVersionParts(a);
        List<Integer> pb = parseVersionParts(b);

        int max = Math.max(pa.size(), pb.size());
        for (int i = 0; i < max; i++) {
            int va = i < pa.size() ? pa.get(i) : 0;
            int vb = i < pb.size() ? pb.get(i) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String v) {
        List<Integer> parts = new ArrayList<>();
        if (v == null) {
            return parts;
        }
        String cleaned = v.trim();
        if (cleaned.isEmpty()) {
            return parts;
        }
        String[] tokens = cleaned.split("\\.");
        for (String token : tokens) {
            String num = token.replaceAll("[^0-9]", "");
            if (num.isEmpty()) {
                parts.add(0);
            } else {
                try {
                    parts.add(Integer.parseInt(num));
                } catch (NumberFormatException e) {
                    parts.add(0);
                }
            }
        }
        return parts;
    }
}
