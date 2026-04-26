package com.pharmax.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class ActivationCodeGenerator {
    private static final String CODE_PREFIX = "HX1";

    // يجب أن يطابق LicenseService.ISSUER_SECRET
    private static final String ISSUER_SECRET = "PharmaX-Offline-Issuer-Secret-ChangeMe";

    public static String generate(String serial6Digits) {
        if (serial6Digits == null || !serial6Digits.matches("\\d{6}")) {
            throw new IllegalArgumentException("serial must be 6 digits");
        }

        String payload = CODE_PREFIX + ":" + serial6Digits;
        byte[] mac = hmacSha256(ISSUER_SECRET, payload);
        String sig8 = toHex(mac).substring(0, 8).toUpperCase();
        return CODE_PREFIX + "-" + serial6Digits + "-" + sig8;
    }

    public static void main(String[] args) {
        int count = 20;
        int start = 1;
        if (args != null && args.length >= 1) {
            count = Integer.parseInt(args[0]);
        }
        if (args != null && args.length >= 2) {
            start = Integer.parseInt(args[1]);
        }

        for (int i = 0; i < count; i++) {
            int serial = start + i;
            String serial6 = String.format("%06d", serial);
            System.out.println(generate(serial6));
        }
    }

    private static byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
