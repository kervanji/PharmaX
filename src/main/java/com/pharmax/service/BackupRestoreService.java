package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackupRestoreService {
    private static final Logger logger = LoggerFactory.getLogger(BackupRestoreService.class);
    private static final String ACTIVE_DB_NAME = "pharmax.db";
    private static final String SQLITE_HEADER = "SQLite format 3";
    private static final String METADATA_SUFFIX = ".metadata.json";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AccessControlService accessControlService = new AccessControlService();
    private final AuditLogService auditLogService = new AuditLogService();

    public File restoreLocalBackup(File backupFile) {
        accessControlService.requireAdmin("BACKUP_RESTORE", "backup", null);
        return restoreBackupInternal(backupFile,
                "BACKUP_RESTORE_ATTEMPT",
                "BACKUP_RESTORE_COMPLETED",
                "BACKUP_RESTORE_FAILED",
                "محاولة استعادة من الملف: ",
                "تمت الاستعادة من الملف: ");
    }

    public File restoreCloudBackup(File extractedBackupFile, String fileId) {
        accessControlService.requireAdmin("BACKUP_RESTORE_CLOUD", "backup", null);
        String suffix = fileId == null ? extractedBackupFile.getAbsolutePath() : fileId;
        return restoreBackupInternal(extractedBackupFile,
                "BACKUP_RESTORE_ATTEMPT",
                "BACKUP_RESTORE_COMPLETED",
                "BACKUP_RESTORE_FAILED",
                "محاولة استعادة سحابية: ",
                "تمت الاستعادة السحابية: ",
                suffix);
    }

    public BackupValidationResult validateBackupFile(File backupFile) {
        if (backupFile == null) {
            throw new IllegalArgumentException("ملف النسخة الاحتياطية غير موجود");
        }
        if (!backupFile.exists()) {
            throw new IllegalArgumentException("ملف النسخة الاحتياطية غير موجود");
        }
        if (!backupFile.isFile()) {
            throw new IllegalArgumentException("المسار المحدد ليس ملف نسخة احتياطية صالح");
        }
        if (!backupFile.canRead()) {
            throw new IllegalArgumentException("لا يمكن قراءة ملف النسخة الاحتياطية");
        }
        if (backupFile.length() <= 0) {
            throw new IllegalArgumentException("ملف النسخة الاحتياطية فارغ");
        }
        if (!hasExpectedExtension(backupFile)) {
            throw new IllegalArgumentException("امتداد ملف النسخة الاحتياطية غير مدعوم");
        }
        if (!isValidSqliteFile(backupFile)) {
            throw new IllegalArgumentException("الملف المحدد ليس قاعدة بيانات SQLite صالحة");
        }

        Set<String> tableNames = listTables(backupFile);
        if (!tableNames.contains("products")) {
            throw new IllegalArgumentException("النسخة الاحتياطية لا تحتوي على جدول products");
        }
        if (!tableNames.contains("vouchers") && !tableNames.contains("sales")) {
            throw new IllegalArgumentException("النسخة الاحتياطية لا تحتوي على جداول الأعمال الأساسية");
        }

        List<String> warnings = new ArrayList<>();
        if (!tableNames.contains("schema_migrations")) {
            warnings.add("جدول schema_migrations غير موجود؛ قد يتم تشغيل الترحيلات عند بدء التشغيل التالي.");
        }

        String latestMigration = getLatestSchemaMigration(backupFile);
        return new BackupValidationResult(
                backupFile,
                backupFile.length(),
                latestMigration,
                new ArrayList<>(tableNames),
                warnings);
    }

    public File createPreRestoreSafetyBackup() {
        File currentDb = getActiveDatabaseFile();
        if (!currentDb.exists()) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        File safetyBackup = new File("pre_restore_safety_" + timestamp + ".db");
        try {
            Files.copy(currentDb.toPath(), safetyBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (!safetyBackup.exists() || safetyBackup.length() <= 0) {
                throw new IOException("فشل التحقق من إنشاء النسخة الاحتياطية الوقائية");
            }
            writeBackupMetadata(safetyBackup, "pre_restore_safety");
            return safetyBackup;
        } catch (Exception e) {
            throw new RuntimeException("تعذر إنشاء نسخة وقائية قبل الاستعادة: " + e.getMessage(), e);
        }
    }

    public String getLatestSchemaMigration() {
        return getLatestSchemaMigration(getActiveDatabaseFile());
    }

    public String getLatestSchemaMigration(File databaseFile) {
        if (databaseFile == null || !databaseFile.exists()) {
            return null;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            if (!tableExists(conn, "schema_migrations")) {
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT migration_name FROM schema_migrations ORDER BY applied_at DESC, migration_name DESC LIMIT 1");
                    ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            logger.warn("Failed to read schema migration from {}", databaseFile.getAbsolutePath(), e);
            return null;
        }
    }

    public boolean isValidSqliteFile(File backupFile) {
        if (backupFile == null || !backupFile.exists() || backupFile.length() < 16) {
            return false;
        }
        byte[] header = new byte[16];
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(backupFile))) {
            int read = inputStream.read(header);
            if (read < 16) {
                return false;
            }
            String headerText = new String(header, StandardCharsets.US_ASCII);
            return headerText.startsWith(SQLITE_HEADER);
        } catch (IOException e) {
            logger.warn("Failed to inspect SQLite header for {}", backupFile.getAbsolutePath(), e);
            return false;
        }
    }

    public File writeBackupMetadata(File backupFile, String backupType) {
        if (backupFile == null || !backupFile.exists()) {
            return null;
        }
        File metadataFile = new File(backupFile.getAbsolutePath() + METADATA_SUFFIX);
        String json = buildBackupMetadataJson(backupFile, backupType);
        try {
            Files.writeString(metadataFile.toPath(), json, StandardCharsets.UTF_8);
            return metadataFile;
        } catch (IOException e) {
            logger.warn("Failed to write backup metadata for {}", backupFile.getAbsolutePath(), e);
            return null;
        }
    }

    public String buildBackupMetadataJson(File backupFile, String backupType) {
        String migrationName = getLatestSchemaMigration(backupFile);
        String appVersion = BackupRestoreService.class.getPackage() != null
                ? BackupRestoreService.class.getPackage().getImplementationVersion()
                : null;
        return "{\n"
                + "  \"app_name\": \"PharmaX\",\n"
                + "  \"backup_created_at\": \"" + escapeJson(LocalDateTime.now().toString()) + "\",\n"
                + "  \"schema_version\": " + toJsonValue(migrationName) + ",\n"
                + "  \"database_file_name\": \"" + escapeJson(backupFile.getName()) + "\",\n"
                + "  \"database_size\": " + backupFile.length() + ",\n"
                + "  \"app_version\": " + toJsonValue(appVersion) + ",\n"
                + "  \"backup_type\": \"" + escapeJson(backupType) + "\"\n"
                + "}\n";
    }

    private File restoreBackupInternal(File backupFile,
            String attemptAction,
            String completedAction,
            String failedAction,
            String attemptPrefix,
            String completedPrefix) {
        return restoreBackupInternal(backupFile, attemptAction, completedAction, failedAction,
                attemptPrefix, completedPrefix, backupFile != null ? backupFile.getAbsolutePath() : "");
    }

    private File restoreBackupInternal(File backupFile,
            String attemptAction,
            String completedAction,
            String failedAction,
            String attemptPrefix,
            String completedPrefix,
            String suffix) {
        try {
            BackupValidationResult validationResult = validateBackupFile(backupFile);
            String warningText = validationResult.warnings().isEmpty()
                    ? ""
                    : " | تحذيرات: " + String.join(" ؛ ", validationResult.warnings());

            auditLogService.record(attemptAction, "backup", null, attemptPrefix + suffix + warningText);

            DatabaseManager.shutdown();
            deleteSidecarLockFiles();

            File preRestoreBackup = createPreRestoreSafetyBackup();
            File currentDb = getActiveDatabaseFile();

            Files.copy(validationResult.file().toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (!currentDb.exists() || currentDb.length() <= 0) {
                throw new IOException("فشل نسخ النسخة الاحتياطية إلى قاعدة البيانات الحالية");
            }

            auditLogService.record(completedAction, "backup", null,
                    completedPrefix + suffix + warningText);
            return preRestoreBackup;
        } catch (Exception e) {
            auditLogService.record(failedAction, "backup", null,
                    attemptPrefix + suffix + " :: " + e.getMessage());
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            if (e instanceof SecurityException securityException) {
                throw securityException;
            }
            throw new RuntimeException("فشل في استعادة البيانات: " + e.getMessage(), e);
        }
    }

    private Set<String> listTables(File backupFile) {
        Set<String> tableNames = new HashSet<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + backupFile.getAbsolutePath());
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null) {
                    tableNames.add(name.toLowerCase());
                }
            }
            return tableNames;
        } catch (Exception e) {
            throw new IllegalArgumentException("تعذر قراءة بنية ملف النسخة الاحتياطية", e);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND lower(name) = lower(?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasExpectedExtension(File backupFile) {
        String name = backupFile.getName().toLowerCase();
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3");
    }

    private File getActiveDatabaseFile() {
        return new File(ACTIVE_DB_NAME);
    }

    private void deleteSidecarLockFiles() {
        FilesHelper.deleteIfExists(new File(ACTIVE_DB_NAME + "-wal"));
        FilesHelper.deleteIfExists(new File(ACTIVE_DB_NAME + "-shm"));
    }

    private String toJsonValue(String value) {
        return value == null || value.isBlank()
                ? "null"
                : "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class FilesHelper {
        private static void deleteIfExists(File file) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete sidecar file {}", file.getAbsolutePath(), e);
            }
        }
    }

    public record BackupValidationResult(
            File file,
            long fileSize,
            String latestSchemaMigration,
            List<String> tables,
            List<String> warnings) {
    }
}
