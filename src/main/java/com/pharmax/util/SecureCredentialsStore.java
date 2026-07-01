package com.pharmax.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.gson.GsonFactory;
import com.pharmax.MainApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Stores Google OAuth credentials encrypted on disk, bound to this machine.
 */
public final class SecureCredentialsStore {
    private static final Logger logger = LoggerFactory.getLogger(SecureCredentialsStore.class);

    private static final String STORE_FILE_NAME = ".drive_cfg";
    private static final String KEY_PEPPER = "PharmaX-Drive-Credentials-Key-v1";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private SecureCredentialsStore() {
    }

    public static File getStoreFile() {
        return new File(PharmaXAppDirs.getAppDataDir(), STORE_FILE_NAME);
    }

    public static byte[] loadCredentialsBytes() throws IOException {
        File storeFile = getStoreFile();
        if (storeFile.isFile() && storeFile.length() > GCM_IV_LENGTH) {
            try {
                byte[] encrypted = Files.readAllBytes(storeFile.toPath());
                byte[] decrypted = decrypt(encrypted);
                if (isValidJsonBytes(decrypted)) {
                    return decrypted;
                }
                logger.warn("Encrypted Google Drive credentials are invalid, removing store file");
                storeFile.delete();
            } catch (Exception e) {
                logger.warn("Failed to decrypt stored credentials, will restore from application bundle", e);
                storeFile.delete();
            }
        }

        byte[] plain = loadPlainCredentialsFromBundle();
        if (plain != null) {
            saveCredentialsBytes(plain);
            return plain;
        }

        throw new IOException(
                "ملف credentials.json غير موجود في البرنامج. أعد تثبيت النسخة الكاملة أو تواصل مع الدعم الفني.");
    }

    public static void saveCredentialsBytes(byte[] plainJson) throws IOException {
        if (!isValidJsonBytes(plainJson)) {
            throw new IOException("بيانات credentials غير صالحة");
        }

        File storeFile = getStoreFile();
        File parent = storeFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        byte[] encrypted = encrypt(plainJson);
        Files.write(storeFile.toPath(), encrypted);
        try {
            Files.setAttribute(storeFile.toPath(), "dos:hidden", true);
        } catch (Exception ignored) {
        }
        logger.info("Stored encrypted Google Drive credentials at {}", storeFile.getAbsolutePath());
    }

    private static byte[] loadPlainCredentialsFromBundle() throws IOException {
        byte[] fromClasspath = readCredentialsFromClasspath();
        if (isValidJsonBytes(fromClasspath)) {
            return fromClasspath;
        }

        File installCredentials = AppInstallPaths.getBundledCredentialsFile();
        if (installCredentials.isFile()) {
            byte[] fromInstall = Files.readAllBytes(installCredentials.toPath());
            if (isValidJsonBytes(fromInstall)) {
                return fromInstall;
            }
            logger.warn("Invalid credentials.json in install directory: {}", installCredentials.getAbsolutePath());
        }

        File appDataPlain = PharmaXAppDirs.getCredentialsFile();
        if (appDataPlain.isFile()) {
            byte[] fromAppData = Files.readAllBytes(appDataPlain.toPath());
            if (isValidJsonBytes(fromAppData)) {
                return fromAppData;
            }
            logger.warn("Removing invalid AppData credentials.json");
            appDataPlain.delete();
        }

        for (String relativePath : new String[] {
                "credentials.json",
                "target/classes/credentials.json",
                "src/main/resources/credentials.json"
        }) {
            File candidate = new File(relativePath);
            if (candidate.isFile()) {
                byte[] fromFile = Files.readAllBytes(candidate.toPath());
                if (isValidJsonBytes(fromFile)) {
                    return fromFile;
                }
            }
        }

        return null;
    }

    private static byte[] readCredentialsFromClasspath() throws IOException {
        String resourcePath = "/credentials.json";
        for (Class<?> anchor : new Class<?>[] {
                SecureCredentialsStore.class,
                com.pharmax.service.drive.GoogleDriveService.class,
                MainApp.class
        }) {
            byte[] bytes = readResource(anchor, resourcePath);
            if (isValidJsonBytes(bytes)) {
                return bytes;
            }
        }

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try (InputStream in = contextLoader.getResourceAsStream("credentials.json")) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    if (isValidJsonBytes(bytes)) {
                        return bytes;
                    }
                }
            }
        }

        return readCredentialsFromJarFile();
    }

    private static byte[] readCredentialsFromJarFile() throws IOException {
        for (Class<?> anchor : new Class<?>[] { MainApp.class, SecureCredentialsStore.class }) {
            try {
                URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
                if (location == null) {
                    continue;
                }
                URI uri = location.toURI();
                File code = new File(uri);
                if (!code.isFile() || !code.getName().toLowerCase().endsWith(".jar")) {
                    continue;
                }
                try (JarFile jarFile = new JarFile(code)) {
                    JarEntry entry = jarFile.getJarEntry("credentials.json");
                    if (entry == null) {
                        continue;
                    }
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        byte[] bytes = in.readAllBytes();
                        if (isValidJsonBytes(bytes)) {
                            logger.info("Loaded credentials.json directly from {}", code.getAbsolutePath());
                            return bytes;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not read credentials.json from jar for {}", anchor.getSimpleName(), e);
            }
        }
        return null;
    }

    private static byte[] readResource(Class<?> anchor, String resourcePath) throws IOException {
        try (InputStream in = anchor.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static String normalizeJsonContent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1).trim();
        }
        return content;
    }

    private static boolean isValidJsonBytes(byte[] bytes) {
        String content = normalizeJsonContent(bytes);
        if (content.isEmpty() || !content.startsWith("{")) {
            return false;
        }
        try {
            GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new StringReader(content));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] encrypt(byte[] plain) throws IOException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plain);
            ByteArrayOutputStream out = new ByteArrayOutputStream(iv.length + cipherText.length);
            out.write(iv);
            out.write(cipherText);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to encrypt credentials", e);
        }
    }

    private static byte[] decrypt(byte[] encrypted) throws IOException {
        if (encrypted.length <= GCM_IV_LENGTH) {
            throw new IOException("Encrypted credentials file is too short");
        }
        try {
            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IOException("Failed to decrypt credentials", e);
        }
    }

    private static SecretKey deriveKey() throws Exception {
        String material = KEY_PEPPER + ":" + DeviceFingerprint.getFingerprintSha256Hex();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(material.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static InputStream openCredentialsStream() throws IOException {
        byte[] bytes = loadCredentialsBytes();
        return new ByteArrayInputStream(normalizeJsonContent(bytes).getBytes(StandardCharsets.UTF_8));
    }
}
