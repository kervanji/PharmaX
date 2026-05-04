package com.pharmax.model;

public class LicenseInfo {
    private String firstRunDate;
    private int trialDays;
    private LicenseStatus licenseStatus;
    private String deviceIdHash;
    private String createdAt;
    private String lastRunDate;
    private String activatedAt;
    private String activationCodeHash;
    private String signature;

    public LicenseInfo() {
    }

    public String getFirstRunDate() {
        return firstRunDate;
    }

    public void setFirstRunDate(String firstRunDate) {
        this.firstRunDate = firstRunDate;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public void setTrialDays(int trialDays) {
        this.trialDays = trialDays;
    }

    public LicenseStatus getLicenseStatus() {
        return licenseStatus;
    }

    public void setLicenseStatus(LicenseStatus licenseStatus) {
        this.licenseStatus = licenseStatus;
    }

    public String getDeviceIdHash() {
        return deviceIdHash;
    }

    public void setDeviceIdHash(String deviceIdHash) {
        this.deviceIdHash = deviceIdHash;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastRunDate() {
        return lastRunDate;
    }

    public void setLastRunDate(String lastRunDate) {
        this.lastRunDate = lastRunDate;
    }

    public String getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(String activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getActivationCodeHash() {
        return activationCodeHash;
    }

    public void setActivationCodeHash(String activationCodeHash) {
        this.activationCodeHash = activationCodeHash;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
