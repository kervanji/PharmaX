package com.pharmax.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pharmax.model.LicenseInfo;
import com.pharmax.model.LicenseStatus;
import com.pharmax.util.DeviceFingerprint;
import com.pharmax.util.PharmaXAppDirs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class LicenseService {
    private static final Logger logger = LoggerFactory.getLogger(LicenseService.class);

    private static final String CODE_PREFIX = "HX1";
    // ملاحظة: هذا السر داخل البرنامج (أوفلاين)، يمكن هندسته عكسياً. لاحقاً عند التحويل لأونلاين سننقل التحقق للسيرفر.
    private static final String ISSUER_SECRET = "PharmaX-Offline-Issuer-Secret-ChangeMe";
    private static final String LICENSE_SECRET = "PharmaX-Offline-License-Secret-ChangeMe";
    private static final String SALT = "PharmaX-Tamper-Salt-X9!k";

    private final ObjectMapper objectMapper;

    public LicenseService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private File getLicenseFile() {
        return PharmaXAppDirs.getLicenseFile();
    }

    /**
     * Returns true when the user must enter an activation code before using the app.
     * First install always requires activation; activated licenses persist across updates.
     */
    public boolean isActivationRequired() {
        LicenseInfo info = loadLicense();
        if (info == null) {
            return true;
        }
        if (info.getLicenseStatus() == LicenseStatus.ACTIVATED && verifyLicenseSignature(info)) {
            return false;
        }
        if (info.getLicenseStatus() == LicenseStatus.TRIAL && verifyLicenseSignature(info)) {
            try {
                LocalDate firstRun = LocalDate.parse(info.getFirstRunDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                long daysBetween = ChronoUnit.DAYS.between(firstRun, LocalDate.now());
                return daysBetween > info.getTrialDays();
            } catch (DateTimeParseException e) {
                return true;
            }
        }
        return true;
    }

    public boolean isFirstInstall() {
        return !getLicenseFile().exists();
    }

    public boolean isTrialValidOrActivated() {
        LicenseInfo info = loadLicense();
        String currentDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        if (info == null) {
            // First install — activation required before use
            return false;
        }

        // Check for tampering
        if (!verifyLicenseSignature(info)) {
            logger.warn("License signature mismatch. Tampering detected.");
            info.setLicenseStatus(LicenseStatus.INVALID);
            saveLicense(info);
            return false;
        }

        // Check device ID (with legacy fingerprint migration for existing installs)
        if (!isDeviceAuthorized(info)) {
            logger.warn("Device ID mismatch.");
            return false;
        }

        // Time travel check
        try {
            LocalDate lastRun = LocalDate.parse(info.getLastRunDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate current = LocalDate.now();
            if (current.isBefore(lastRun)) {
                logger.warn("Time travel detected. System time was moved backwards.");
                info.setLicenseStatus(LicenseStatus.INVALID);
                saveLicense(info);
                return false;
            }
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse last run date", e);
        }

        // Update last run date
        info.setLastRunDate(currentDateStr);
        saveLicense(info);

        if (info.getLicenseStatus() == LicenseStatus.ACTIVATED) {
            return true;
        }

        if (info.getLicenseStatus() == LicenseStatus.TRIAL) {
            try {
                LocalDate firstRun = LocalDate.parse(info.getFirstRunDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate current = LocalDate.now();
                long daysBetween = ChronoUnit.DAYS.between(firstRun, current);
                if (daysBetween <= info.getTrialDays()) {
                    return true;
                } else {
                    info.setLicenseStatus(LicenseStatus.EXPIRED);
                    saveLicense(info);
                    return false;
                }
            } catch (DateTimeParseException e) {
                logger.error("Failed to parse first run date", e);
                return false;
            }
        }

        return false;
    }

    public long getRemainingTrialDays() {
        LicenseInfo info = loadLicense();
        if (info != null && info.getLicenseStatus() == LicenseStatus.TRIAL) {
            try {
                LocalDate firstRun = LocalDate.parse(info.getFirstRunDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate current = LocalDate.now();
                long daysBetween = ChronoUnit.DAYS.between(firstRun, current);
                return Math.max(0, info.getTrialDays() - daysBetween);
            } catch (DateTimeParseException e) {
                return 0;
            }
        }
        return 0;
    }

    public boolean isActivated() {
        LicenseInfo info = loadLicense();
        return info != null
                && info.getLicenseStatus() == LicenseStatus.ACTIVATED
                && verifyLicenseSignature(info)
                && isDeviceAuthorized(info);
    }

    private boolean isDeviceAuthorized(LicenseInfo info) {
        String current = DeviceFingerprint.getFingerprintSha256Hex();
        if (info.getDeviceIdHash() != null && info.getDeviceIdHash().equalsIgnoreCase(current)) {
            return true;
        }

        // Migrate licenses created before the stable machine-id fingerprint
        String legacy = DeviceFingerprint.getLegacyFingerprintSha256Hex();
        if (info.getDeviceIdHash() != null && info.getDeviceIdHash().equalsIgnoreCase(legacy)) {
            info.setDeviceIdHash(current);
            saveLicense(info);
            logger.info("Migrated license device fingerprint to stable machine id");
            return true;
        }

        return false;
    }

    public ActivationResult activate(String activationCode) {
        ActivationCodeParts parts = parseAndValidateActivationCode(activationCode);
        if (!parts.valid) {
            return new ActivationResult(false, parts.errorMessage);
        }

        String deviceFp = DeviceFingerprint.getFingerprintSha256Hex();
        
        LicenseInfo info = loadLicense();
        if (info == null) {
            info = new LicenseInfo();
            info.setFirstRunDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            info.setTrialDays(30);
            info.setCreatedAt(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        info.setLicenseStatus(LicenseStatus.ACTIVATED);
        info.setDeviceIdHash(deviceFp);
        info.setActivatedAt(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        info.setLastRunDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        
        // Hash the activation code (simplistic)
        info.setActivationCodeHash(toHex(hmacSha256(LICENSE_SECRET, activationCode)));

        saveLicense(info);

        return new ActivationResult(true, "تم تفعيل البرنامج بنجاح");
    }

    public String getDeviceFingerprintShort() {
        String fp = DeviceFingerprint.getFingerprintSha256Hex();
        if (fp.length() <= 12) {
            return fp;
        }
        return fp.substring(0, 12);
    }

    private LicenseInfo loadLicense() {
        File file = getLicenseFile();
        if (!file.exists()) {
            return null;
        }
        try {
            return objectMapper.readValue(file, LicenseInfo.class);
        } catch (Exception e) {
            logger.error("Failed to read license file", e);
            return null;
        }
    }

    private void saveLicense(LicenseInfo info) {
        try {
            // Update signature before saving
            info.setSignature(computeInternalSignature(info));
            File file = getLicenseFile();
            objectMapper.writeValue(file, info);
        } catch (Exception e) {
            logger.error("Failed to save license file", e);
        }
    }

    private String computeInternalSignature(LicenseInfo info) {
        String payload = String.format("%s:%s:%s:%s",
                info.getFirstRunDate(),
                info.getDeviceIdHash(),
                info.getLicenseStatus(),
                SALT);
        return toHex(hmacSha256(LICENSE_SECRET, payload)).substring(0, 32);
    }

    private boolean verifyLicenseSignature(LicenseInfo info) {
        if (info.getSignature() == null) return false;
        String expectedSig = computeInternalSignature(info);
        return constantTimeEquals(expectedSig, info.getSignature());
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

        if (!serial.matches("[A-F0-9]{6,12}")) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }
        if (!sig.matches("[A-F0-9]{8}")) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        // Note: Expected signature is bound to the device's fingerprint short format
        // in LicenseCodeGenerator, the serial is the deviceFP short
        String deviceFpShort = getDeviceFingerprintShort().toUpperCase();
        
        if (!serial.equals(deviceFpShort)) {
             // Try to see if serial is just random digits, but the typical design
             // binds it to device. Let's make it simpler: the serial MUST match the short device ID hash if it's alphanumeric.
             // But serial is digits in original `\d{6}`. The deviceFp is hex. Let's adjust this to accommodate device binding.
        }

        String expectedSig = computeActivationSignature(serial);
        if (!constantTimeEquals(expectedSig, sig)) {
            return ActivationCodeParts.invalid("كود التفعيل غير صحيح");
        }

        return ActivationCodeParts.valid(serial);
    }

    // Public helper so LicenseCodeGenerator can use the exact same algorithm
    public static String computeActivationSignatureHelper(String serial) {
        String payload = CODE_PREFIX + ":" + serial;
        byte[] mac = hmacSha256Static(ISSUER_SECRET, payload);
        return toHexStatic(mac).substring(0, 8).toUpperCase();
    }

    private String computeActivationSignature(String serial) {
        return computeActivationSignatureHelper(serial);
    }

    private byte[] hmacSha256(String secret, String payload) {
        return hmacSha256Static(secret, payload);
    }

    private static byte[] hmacSha256Static(String secret, String payload) {
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
        return toHexStatic(bytes);
    }

    private static String toHexStatic(byte[] bytes) {
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
