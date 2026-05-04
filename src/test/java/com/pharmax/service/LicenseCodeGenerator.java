package com.pharmax.service;

public class LicenseCodeGenerator {

    /**
     * Helper to generate a permanent activation code.
     * To be used by the developer offline.
     * 
     * @param requestCode The short device fingerprint shown in the activation UI
     * @return The activation code (e.g. HX1-A1B2C3D4E5F6-12345678)
     */
    public static String generateCode(String requestCode) {
        if (requestCode == null || requestCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Request code cannot be empty");
        }
        
        String serial = requestCode.trim().toUpperCase();
        String sig = LicenseService.computeActivationSignatureHelper(serial);
        
        return "HX1-" + serial + "-" + sig;
    }

    public static void main(String[] args) {
        // Test example
        String deviceCode = "A1B2C3D4E5F6";
        if (args.length > 0) {
            deviceCode = args[0];
        }
        
        System.out.println("Generating activation code for device: " + deviceCode);
        String activationCode = generateCode(deviceCode);
        System.out.println("Activation Code: " + activationCode);
    }
}
