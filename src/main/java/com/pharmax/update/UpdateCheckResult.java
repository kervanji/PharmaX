package com.pharmax.update;

public final class UpdateCheckResult {
    private final boolean updateAvailable;
    private final String latestVersion;
    private final String tag;
    private final String downloadUrl;
    private final String releaseNotes;

    public UpdateCheckResult(boolean updateAvailable, String latestVersion, String tag, String downloadUrl, String releaseNotes) {
        this.updateAvailable = updateAvailable;
        this.latestVersion = latestVersion;
        this.tag = tag;
        this.downloadUrl = downloadUrl;
        this.releaseNotes = releaseNotes;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getTag() {
        return tag;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }
}
