package com.pharmax.service;

import com.pharmax.util.AppConfigStore;
import com.pharmax.util.DeviceFingerprint;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class LicenseService {
    private static final String KEY_LICENSE_ACTIVATED = "license.activated";
    private static final String KEY_LICENSE_DEVICE_FP = "license.device_fp_sha256";
    private static final String KEY_LICENSE_SERIAL = "license.serial";
    private static final String KEY_LICENSE_SIG = "license.signature";

    private static final String CODE_PREFIX = "HX1";

    // ملاحظة: هذا السر داخل البرنامج (أوفلاين)، يمكن هندسته عكسياً. لاحقاً عند التحويل لأونلاين سننقل التحقق للسيرفر.
    private static final String ISSUER_SECRET = "PharmaX-Offline-Issuer-Secret-ChangeMe";
    private static final String LICENSE_SECRET = "PharmaX-Offline-License-Secret-ChangeMe";

    private final AppConfigStore configStore = new AppConfigStore();

    public boolean isActivated() {
        Properties p = configStore.load();
        if (!"true".equalsIgnoreCase(p.getProperty(KEY_LICENSE_ACTIVATED, "false"))) {
            return false;
        }

        String deviceFp = p.getProperty(KEY_LICENSE_DEVICE_FP, "").trim();
        String serial = p.getProperty(KEY_LICENSE_SERIAL, "").trim();
        String sig = p.getProperty(KEY_LICENSE_SIG, "").trim();

        if (deviceFp.isEmpty() || serial.isEmpty() || sig.isEmpty()) {
            return false;
        }

        String currentDeviceFp = DeviceFingerprint.getFingerprintSha256Hex();
        if (!currentDeviceFp.equalsIgnoreCase(deviceFp)) {
            return false;
        }

        String expectedSig = computeLicenseSignature(currentDeviceFp, serial);
        return constantTimeEquals(expectedSig, sig);
    }

    public ActivationResult activate(String activationCode) {
        ActivationCodeParts parts = parseAndValidateActivationCode(activationCode);
        if (!parts.valid) {
            return new ActivationResult(false, parts.errorMessage);
        }

        String deviceFp = DeviceFingerprint.getFingerprintSha256Hex();
        String licenseSig = computeLicenseSignature(deviceFp, parts.serial);

        Properties p = configStore.load();
        p.setProperty(KEY_LICENSE_ACTIVATED, "true");
        p.setProperty(KEY_LICENSE_DEVICE_FP, deviceFp);
        p.setProperty(KEY_LICENSE_SERIAL, parts.serial);
        p.setProperty(KEY_LICENSE_SIG, licenseSig);
        configStore.save(p);

        return new ActivationResult(true, "تم تفعيل البرنامج بنجاح");
    }

    public String getDeviceFingerprintShort() {
        String fp = DeviceFingerprint.getFingerprintSha256Hex();
        if (fp.length() <= 12) {
            return fp;
        }
        return fp.substring(0, 12);
    }

    private ActivationCodeParts parseAndValidateActivationCode(String code) {
        if (code == null) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        String normalized = code.trim().toUpperCase();
        String[] parts = normalized.split("-");
        if (parts.length != 3) {
            return ActivationCodeParts.invalid("صيغة كود التفعيل غير صحيحة");
        }

        if (!CODE_PREFIX.equals(parts[0])) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        String serial = parts[1];
        String sig = parts[2];

        if (!serial.matches("\\d{6}")) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }
        if (!sig.matches("[A-F0-9]{8}")) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        String expectedSig = computeActivationSignature(serial);
        if (!constantTimeEquals(expectedSig, sig)) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        return ActivationCodeParts.valid(serial);
    }

    private String computeActivationSignature(String serial) {
        String payload = CODE_PREFIX + ":" + serial;
        byte[] mac = hmacSha256(ISSUER_SECRET, payload);
        return toHex(mac).substring(0, 8).toUpperCase();
    }

    private String computeLicenseSignature(String deviceFpSha256Hex, String serial) {
        String payload = deviceFpSha256Hex.toLowerCase() + ":" + serial;
        byte[] mac = hmacSha256(LICENSE_SECRET, payload);
        return toHex(mac).substring(0, 16);
    }

    private byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static final class ActivationResult {
        public final boolean success;
        public final String message;

        public ActivationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private static final class ActivationCodeParts {
        final boolean valid;
        final String serial;
        final String errorMessage;

        private ActivationCodeParts(boolean valid, String serial, String errorMessage) {
            this.valid = valid;
            this.serial = serial;
            this.errorMessage = errorMessage;
        }

        static ActivationCodeParts valid(String serial) {
            return new ActivationCodeParts(true, serial, null);
        }

        static ActivationCodeParts invalid(String msg) {
            return new ActivationCodeParts(false, null, msg);
        }
    }
}
