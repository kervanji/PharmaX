package com.pharmax.service.drive;

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

    public BackupService(GoogleDriveService driveService) {
        this.driveService = driveService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Backup-Scheduler");
            t.setDaemon(true);
            return t;
        });
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
        if (!isDriveConnected())
            throw new IOException("Drive not connected");

        logger.info("Initiating cloud restore...");

        // 1. Backup current data (Safety First)
        logger.info("Creating safety backup before restore...");
        performBackup();

        // 2. Download the backup file
        File tempZip = File.createTempFile("restore_download_", ".zip");
        try {
            logger.info("Downloading backup file...");
            driveService.downloadFile(fileId, tempZip);

            // 3. Unzip and verify
            // For safety, let's extract to a temp db file first
            File tempDb = File.createTempFile("restore_db_", ".db");

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
                ZipEntry entry = zis.getNextEntry();
                // Assumes zip contains one file named PharmaX_snapshot_... or similar, or just
                // pharmax.db?
                // Our backup creates zip with entry name = snapshot filename.
                // We should extract whatever is in there to tempDb.
                if (entry != null) {
                    Files.copy(zis, tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new IOException("Empty zip file");
                }
            }

            // 4. Critical Section: Replace Database
            // We must try to close connections if possible
            // In a JavaFX app with simple SQLite, often replacing file requires closing App
            // or Connections.
            // DatabaseManager.shutdown();

            com.pharmax.database.DatabaseManager.shutdown();

            // Give it a moment to release locks
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            File currentDb = new File(DB_PATH);
            File backupOfCurrent = new File(DB_PATH + ".bak");

            // Local file backup just in case
            if (currentDb.exists()) {
                Files.copy(currentDb.toPath(), backupOfCurrent.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.copy(tempDb.toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

            logger.info("Database restored successfully from cloud backup.");

            // Cleanup temps
            tempDb.delete();

        } finally {
            tempZip.delete();
        }
    }
}
