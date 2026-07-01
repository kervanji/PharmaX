package com.pharmax.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class DeviceFingerprint {
    private DeviceFingerprint() {
    }

    public static String getFingerprintSource() {
        StringBuilder sb = new StringBuilder();

        sb.append("os.name=").append(System.getProperty("os.name", "")).append('\n');
        sb.append("os.arch=").append(System.getProperty("os.arch", "")).append('\n');

        String machineId = getStableMachineId();
        if (!machineId.isEmpty()) {
            sb.append("machine.id=").append(machineId).append('\n');
        }

        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                while (ifaces.hasMoreElements()) {
                    NetworkInterface ni = ifaces.nextElement();
                    if (ni == null || ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                        continue;
                    }
                    byte[] mac = ni.getHardwareAddress();
                    if (mac == null || mac.length == 0) {
                        continue;
                    }
                    macs.add(toHex(mac));
                }
            }
        } catch (Exception ignored) {
        }

        macs.sort(String::compareTo);
        for (String m : macs) {
            sb.append("mac=").append(m).append('\n');
        }

        return sb.toString();
    }

    /**
     * Legacy fingerprint (included user.name) — used only to migrate existing licenses after updates.
     */
    public static String getLegacyFingerprintSource() {
        StringBuilder sb = new StringBuilder();

        sb.append("os.name=").append(System.getProperty("os.name", "")).append('\n');
        sb.append("os.arch=").append(System.getProperty("os.arch", "")).append('\n');
        sb.append("user.name=").append(System.getProperty("user.name", "")).append('\n');

        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                while (ifaces.hasMoreElements()) {
                    NetworkInterface ni = ifaces.nextElement();
                    if (ni == null || ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                        continue;
                    }
                    byte[] mac = ni.getHardwareAddress();
                    if (mac == null || mac.length == 0) {
                        continue;
                    }
                    macs.add(toHex(mac));
                }
            }
        } catch (Exception ignored) {
        }

        macs.sort(String::compareTo);
        for (String m : macs) {
            sb.append("mac=").append(m).append('\n');
        }

        return sb.toString();
    }

    public static String getFingerprintSha256Hex() {
        return sha256Hex(getFingerprintSource());
    }

    public static String getLegacyFingerprintSha256Hex() {
        return sha256Hex(getLegacyFingerprintSource());
    }

    private static String getStableMachineId() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return readWindowsMachineGuid();
        }
        return "";
    }

    private static String readWindowsMachineGuid() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v", "MachineGuid")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("MachineGuid")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            return parts[parts.length - 1].trim();
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256", e);
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
