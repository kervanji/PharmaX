package com.pharmax.service.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.pharmax.util.PharmaXAppDirs;
import com.pharmax.util.SecureCredentialsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "PharmaX Inventory Management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = PharmaXAppDirs.getDriveTokensDir().getAbsolutePath();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String BACKUP_FOLDER_NAME = "PharmaX Backups";

    private Drive driveService;
    private String backupFolderId;
    private volatile boolean initializing = false;

    public GoogleDriveService() {
        migrateLegacyTokenDirectory();
    }

    private void migrateLegacyTokenDirectory() {
        java.io.File legacyDir = new java.io.File(System.getProperty("user.home"), ".PharmaX/drive_tokens");
        java.io.File targetDir = PharmaXAppDirs.getDriveTokensDir();
        if (!legacyDir.isDirectory() || legacyDir.equals(targetDir)) {
            return;
        }
        java.io.File[] legacyFiles = legacyDir.listFiles();
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        java.io.File[] existing = targetDir.listFiles();
        if (existing != null && existing.length > 0) {
            return;
        }
        for (java.io.File legacyFile : legacyFiles) {
            if (legacyFile.isFile()) {
                try {
                    Files.copy(legacyFile.toPath(),
                            new java.io.File(targetDir, legacyFile.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    logger.warn("Failed to migrate legacy Google Drive token {}", legacyFile.getName(), e);
                }
            }
        }
        logger.info("Migrated Google Drive tokens from legacy directory");
    }

    private GoogleAuthorizationCodeFlow buildFlow(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        try (InputStream in = openCredentialsStream()) {
            GoogleClientSecrets clientSecrets = loadClientSecrets(in);

            java.io.File tokenDir = PharmaXAppDirs.getDriveTokensDir();
            if (!tokenDir.exists()) {
                tokenDir.mkdirs();
            }

            return new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
                    .setAccessType("offline")
                    .build();
        }
    }

    private InputStream openCredentialsStream() throws IOException {
        return SecureCredentialsStore.openCredentialsStream();
    }

    private GoogleClientSecrets loadClientSecrets(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (content.isEmpty() || !content.startsWith("{")) {
            throw new IOException(
                    "ملف credentials.json غير صالح. أعد تثبيت البرنامج أو تواصل مع الدعم الفني.");
        }

        try {
            return GoogleClientSecrets.load(JSON_FACTORY, new StringReader(content));
        } catch (Exception e) {
            throw new IOException(
                    "تعذر قراءة credentials.json: " + e.getMessage(), e);
        }
    }

    private Credential loadStoredCredential(GoogleAuthorizationCodeFlow flow) throws IOException {
        try {
            return flow.loadCredential("user");
        } catch (Exception e) {
            if (isJsonParseError(e)) {
                logger.warn("Corrupted Google Drive token store detected, clearing saved tokens");
                clearStoredCredentials(flow);
                return null;
            }
            throw e;
        }
    }

    private boolean isJsonParseError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("MalformedJsonException") || name.contains("JsonParseException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("MalformedJsonException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void clearStoredCredentials(GoogleAuthorizationCodeFlow flow) {
        try {
            flow.getCredentialDataStore().delete("user");
        } catch (Exception clearError) {
            logger.warn("Failed to remove stored Google Drive credential from data store", clearError);
        }

        try {
            deleteTokenDirectoryContents(Path.of(TOKENS_DIRECTORY_PATH));
        } catch (IOException clearError) {
            logger.warn("Failed to clean Google Drive token directory", clearError);
        }
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        GoogleAuthorizationCodeFlow flow = buildFlow(HTTP_TRANSPORT);

        // Try loading stored credential first (no port bind needed)
        Credential storedCredential = loadStoredCredential(flow);
        if (storedCredential != null && (storedCredential.getRefreshToken() != null
                || storedCredential.getExpiresInSeconds() == null
                || storedCredential.getExpiresInSeconds() > 60)) {
            logger.info("Loaded stored Google Drive credential (no browser needed)");
            return storedCredential;
        }

        // No valid stored credential — need browser OAuth (use port 0 for dynamic port)
        logger.info("No stored credential found, starting browser OAuth flow...");
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Initialize with browser OAuth if needed (user-triggered).
     */
    public synchronized void initialize() throws GeneralSecurityException, IOException {
        if (driveService != null) {
            logger.info("Google Drive already connected, skipping initialization");
            return;
        }
        if (initializing) {
            logger.warn("Google Drive initialization already in progress, skipping");
            return;
        }
        initializing = true;
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // Initialize backup folder
            getOrCreateBackupFolder();
        } catch (Exception e) {
            resetConnectionState();
            handleInvalidGrantIfNeeded(e);
            throw e;
        } finally {
            initializing = false;
        }
    }

    /**
     * Try to reconnect silently from saved tokens only. Never opens a browser.
     * Returns true if reconnected successfully, false otherwise.
     */
    public synchronized boolean initializeSilently() {
        if (driveService != null) {
            return true;
        }
        if (initializing) {
            return false;
        }
        initializing = true;
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = buildFlow(HTTP_TRANSPORT);
            Credential storedCredential = loadStoredCredential(flow);
            if (storedCredential != null && (storedCredential.getRefreshToken() != null
                    || storedCredential.getExpiresInSeconds() == null
                    || storedCredential.getExpiresInSeconds() > 60)) {
                driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, storedCredential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
                getOrCreateBackupFolder();
                logger.info("Google Drive reconnected silently from saved tokens");
                return true;
            } else {
                logger.info("No valid stored credential, silent reconnect skipped (browser auth needed)");
                return false;
            }
        } catch (Exception e) {
            logger.warn("Silent Google Drive reconnect failed", e);
            resetConnectionState();
            handleInvalidGrantIfNeeded(e);
            return false;
        } finally {
            initializing = false;
        }
    }

    public boolean isConnected() {
        return driveService != null;
    }

    private String getOrCreateBackupFolder() throws IOException {
        if (backupFolderId != null)
            return backupFolderId;

        // Check if folder exists
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + BACKUP_FOLDER_NAME
                + "' and trashed=false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        for (File file : result.getFiles()) {
            logger.info("Found backup folder: " + file.getName() + " (" + file.getId() + ")");
            backupFolderId = file.getId();
            return backupFolderId;
        }

        // Create folder if not exists
        File fileMetadata = new File();
        fileMetadata.setName(BACKUP_FOLDER_NAME);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File file = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();

        logger.info("Created backup folder: " + BACKUP_FOLDER_NAME + " (" + file.getId() + ")");
        backupFolderId = file.getId();
        return backupFolderId;
    }

    private void resetConnectionState() {
        driveService = null;
        backupFolderId = null;
    }

    private void handleInvalidGrantIfNeeded(Exception exception) {
        if (!isInvalidGrant(exception)) {
            return;
        }

        logger.warn("Stored Google Drive tokens are no longer valid; clearing saved credentials");
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = buildFlow(HTTP_TRANSPORT);
            clearStoredCredentials(flow);
        } catch (Exception clearError) {
            logger.warn("Failed to remove stored Google Drive credential from data store", clearError);
        }
    }

    private boolean isInvalidGrant(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TokenResponseException tokenException
                    && tokenException.getDetails() != null
                    && "invalid_grant".equals(tokenException.getDetails().getError())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void deleteTokenDirectoryContents(Path tokenDir) throws IOException {
        if (!Files.exists(tokenDir)) {
            return;
        }

        try (var paths = Files.list(tokenDir)) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public String uploadFile(java.io.File uploadFile, String description) throws IOException {
        if (driveService == null)
            throw new IOException("Drive service not initialized");

        String folderId = getOrCreateBackupFolder();

        File fileMetadata = new File();
        fileMetadata.setName(uploadFile.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));
        fileMetadata.setDescription(description);

        FileContent mediaContent = new FileContent("application/zip", uploadFile);

        File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        logger.info("File uploaded: " + file.getId());
        return file.getId();
    }

    public void downloadFile(String fileId, java.io.File destination) throws IOException {
        if (driveService == null)
            throw new IOException("Drive not connected");
        try (FileOutputStream fos = new FileOutputStream(destination)) {
            driveService.files().get(fileId).executeMediaAndDownloadTo(fos);
        }
    }

    public List<BackupFile> listBackups() throws IOException {
        if (driveService == null)
            return Collections.emptyList();

        // Search ALL of Drive for PharmaX_backup_ files
        Map<String, File> backupMap = new HashMap<>(); // deduplicate by file ID
        String pageToken = null;

        // Search 1: Global search across all of Drive
        do {
            FileList result = driveService.files().list()
                    .setQ("name contains 'PharmaX_backup_' and trashed=false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, createdTime, size)")
                    .setPageToken(pageToken)
                    .execute();

            for (File f : result.getFiles()) {
                backupMap.put(f.getId(), f);
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        // Search 2: Also search inside the backup folder specifically
        try {
            String folderId = getOrCreateBackupFolder();
            pageToken = null;
            do {
                FileList result = driveService.files().list()
                        .setQ("name contains 'PharmaX_backup_' and '" + folderId + "' in parents and trashed=false")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, createdTime, size)")
                        .setPageToken(pageToken)
                        .execute();

                for (File f : result.getFiles()) {
                    backupMap.put(f.getId(), f);
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (Exception e) {
            logger.warn("Could not search backup folder", e);
        }

        List<File> allBackups = new ArrayList<>(backupMap.values());
        logger.info("Found " + allBackups.size() + " files matching 'PharmaX_backup_' in Google Drive");

        List<BackupFile> parsedBackups = new ArrayList<>();
        // Format 1: yyyy-MM-dd_HH-mm-ss (current BackupService format)
        Pattern pattern1 = Pattern.compile("PharmaX_backup_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})");
        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        // Format 2: yyyyMMdd_HHmmss (legacy format)
        Pattern pattern2 = Pattern.compile("PharmaX_backup_(\\d{8}_\\d{6})");
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

        for (File file : allBackups) {
            logger.debug("Processing Drive file: " + file.getName() + " (id=" + file.getId() + ")");
            boolean parsed = false;

            // Try format 1 first (current)
            Matcher matcher1 = pattern1.matcher(file.getName());
            if (matcher1.find()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(matcher1.group(1), formatter1);
                    parsedBackups.add(new BackupFile(file, timestamp));
                    parsed = true;
                } catch (Exception e) {
                    logger.warn("Failed to parse timestamp (format1) from: " + file.getName(), e);
                }
            }

            // Try format 2 (legacy)
            if (!parsed) {
                Matcher matcher2 = pattern2.matcher(file.getName());
                if (matcher2.find()) {
                    try {
                        LocalDateTime timestamp = LocalDateTime.parse(matcher2.group(1), formatter2);
                        parsedBackups.add(new BackupFile(file, timestamp));
                        parsed = true;
                    } catch (Exception e) {
                        logger.warn("Failed to parse timestamp (format2) from: " + file.getName(), e);
                    }
                }
            }

            if (!parsed) {
                logger.warn("Could not parse backup filename: " + file.getName());
            }
        }
        logger.info("Parsed " + parsedBackups.size() + " valid backups from " + allBackups.size() + " files");
        parsedBackups.sort((b1, b2) -> b2.timestamp.compareTo(b1.timestamp)); // Newest first
        return parsedBackups;
    }

    public void cleanupOldBackups() {
        if (driveService == null)
            return;

        try {
            List<BackupFile> parsedBackups = listBackups(); // Reuse listBackups logic

            if (parsedBackups.isEmpty())
                return;

            logger.info("Found " + parsedBackups.size() + " backups to check for retention.");

            List<String> toDeleteIds = new ArrayList<>();
            List<BackupFile> toKeep = new ArrayList<>();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime seventyTwoHoursAgo = now.minusHours(72);
            LocalDateTime thirtyDaysAgo = now.minusDays(30);

            Map<String, BackupFile> dailyBackups = new HashMap<>(); // Key: yyyyMMdd

            for (BackupFile backup : parsedBackups) {
                if (backup.getTimestamp().isAfter(seventyTwoHoursAgo)) {
                    // Keep all valid backups from last 72 hours
                    toKeep.add(backup);
                } else if (backup.getTimestamp().isAfter(thirtyDaysAgo)) {
                    // Keep one per day for last 30 days (fast-forwarding logic: keep the latest for
                    // that day)
                    String dayKey = backup.getTimestamp().format(DateTimeFormatter.BASIC_ISO_DATE);
                    if (!dailyBackups.containsKey(dayKey)) {
                        dailyBackups.put(dayKey, backup);
                        toKeep.add(backup);
                    } else {
                        toDeleteIds.add(backup.getId());
                    }
                } else {
                    // Delete backups older than 30 days
                    toDeleteIds.add(backup.getId());
                }
            }

            // Execute deletion
            logger.info(" retention policy: Keeping " + toKeep.size() + ", Deleting " + toDeleteIds.size());

            for (String fileId : toDeleteIds) {
                try {
                    driveService.files().delete(fileId).execute();
                    logger.debug("Deleted old backup: " + fileId);
                } catch (IOException e) {
                    logger.error("Failed to delete file: " + fileId, e);
                }
            }

        } catch (IOException e) {
            logger.error("Failed to execute backup cleanup", e);
        }
    }

    public static class BackupFile {
        private String id;
        private String name;
        private LocalDateTime timestamp;
        private long size;

        public BackupFile(File file, LocalDateTime timestamp) {
            this.id = file.getId();
            this.name = file.getName();
            this.timestamp = timestamp;
            this.size = file.getSize() != null ? file.getSize() : 0;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public long getSize() {
            return size;
        }

        @Override
        public String toString() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (" + (size / 1024) + " KB)";
        }
    }
}
