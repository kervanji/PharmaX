package com.pharmax.service.drive;

import com.pharmax.service.AccessControlService;
import com.pharmax.service.BackupRestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Collections;
import java.util.List;

public class BackupService {
    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final String DB_PATH = "pharmax.db"; // Assuming DB is at root of project run dir
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final GoogleDriveService driveService;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isBackupRunning = new AtomicBoolean(false);
    private final AccessControlService accessControlService;
    private final BackupRestoreService backupRestoreService;

    public BackupService(GoogleDriveService driveService) {
        this.driveService = driveService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Backup-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.accessControlService = new AccessControlService();
        this.backupRestoreService = new BackupRestoreService();
    }

    public boolean isDriveConnected() {
        return driveService != null && driveService.isConnected();
    }

    public void startHourlyBackup() {
        // Schedule first backup after 1 hour, then every 1 hour
        scheduler.scheduleAtFixedRate(this::performBackup, 1, 1, TimeUnit.HOURS);
        logger.info("Hourly backup scheduled.");
    }

    public void performBackup() {
        if (!driveService.isConnected()) {
            logger.warn("Skipping backup: Google Drive not connected.");
            return;
        }

        if (isBackupRunning.compareAndSet(false, true)) {
            try {
                logger.info("Starting backup process...");

                // 1. Create a safe snapshot of the DB
                File snapshotFile = createDbSnapshot();

                if (snapshotFile != null) {
                    // 2. Zip the snapshot
                    File zipFile = createZipFile(snapshotFile);

                    // 3. Upload to Google Drive
                    if (zipFile != null) {
                        String description = "Auto-backup created at "
                                + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        driveService.uploadFile(zipFile, description);

                        // Cleanup temp files
                        Files.deleteIfExists(zipFile.toPath());
                    }
                    // Cleanup snapshot
                    Files.deleteIfExists(snapshotFile.toPath());
                }

                logger.info("Backup process completed successfully.");
            } catch (Exception e) {
                logger.error("Backup failed", e);
            } finally {
                isBackupRunning.set(false);
            }
        } else {
            logger.warn("Backup already in progress. Skipping this schedule.");
        }
    }

    private File createDbSnapshot() {
        // Option 1: Use SQLite VACUUM INTO for consistent snapshot without locking too
        // long
        // Requires SQLite 3.27.0+
        String snapshotName = "PharmaX_snapshot_" + System.currentTimeMillis() + ".db";
        File snapshotFile = new File(snapshotName);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                Statement stmt = conn.createStatement()) {

            // VACUUM INTO 'filename' creates a transactionally consistent copy
            stmt.execute("VACUUM INTO '" + snapshotFile.getAbsolutePath() + "'");
            return snapshotFile;

        } catch (Exception e) {
            logger.error("Failed to create DB snapshot using VACUUM INTO. Trying fallback copy...", e);

            // Fallback: Checkpoint WAL and copy file (Less safe if write heavy, but
            // acceptable for single user desktop app mostly)
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                    Statement stmt = conn.createStatement()) {

                stmt.execute("PRAGMA wal_checkpoint(FULL)");

                Files.copy(new File(DB_PATH).toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return snapshotFile;
            } catch (Exception ex) {
                logger.error("Fallback snapshot creation failed", ex);
                return null;
            }
        }
    }

    private File createZipFile(File sourceFile) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String zipName = "PharmaX_backup_" + timestamp + ".zip";
        File zipFile = new File(zipName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos);
                FileInputStream fis = new FileInputStream(sourceFile)) {

            ZipEntry zipEntry = new ZipEntry(sourceFile.getName());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();

            ZipEntry metadataEntry = new ZipEntry("backup_metadata.json");
            zos.putNextEntry(metadataEntry);
            byte[] metadataBytes = backupRestoreService.buildBackupMetadataJson(sourceFile, "cloud")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            zos.write(metadataBytes);
            zos.closeEntry();
        }
        return zipFile;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Try one last backup on exit if connected
        if (driveService.isConnected()) {
            logger.info("Performing backup on exit...");
            performBackup();
        }
    }

    public List<GoogleDriveService.BackupFile> listCloudBackups() {
        if (!isDriveConnected())
            return Collections.emptyList();
        try {
            return driveService.listBackups();
        } catch (IOException e) {
            logger.error("Failed to list backups", e);
            return Collections.emptyList();
        }
    }

    public void restoreFromCloud(String fileId) throws Exception {
        accessControlService.requireAdmin("BACKUP_RESTORE_CLOUD", "backup", null);
        if (!isDriveConnected())
            throw new IOException("Drive not connected");

        logger.info("Initiating cloud restore...");

        // 2. Download the backup file
        File tempZip = File.createTempFile("restore_download_", ".zip");
        try {
            logger.info("Downloading backup file...");
            driveService.downloadFile(fileId, tempZip);

            // 3. Extract the DB entry to a temp file, then pass through shared restore validation
            File tempDb = File.createTempFile("restore_db_", ".db");
            extractDatabaseEntry(tempZip, tempDb);
            backupRestoreService.restoreCloudBackup(tempDb, fileId);
            Files.deleteIfExists(tempDb.toPath());
            logger.info("Database restored successfully from cloud backup.");

        } finally {
            Files.deleteIfExists(tempZip.toPath());
        }
    }

    private void extractDatabaseEntry(File zipFile, File targetDb) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".db")) {
                    Files.copy(zis, targetDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("ملف النسخة السحابية لا يحتوي على قاعدة بيانات صالحة");
    }
}
