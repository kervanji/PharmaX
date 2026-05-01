package com.pharmax.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class UpdateInstallerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(UpdateInstallerLauncher.class);

    private UpdateInstallerLauncher() {
    }

    public static void launchInstallerAndRestart(Path installerExe) {
        if (installerExe == null) {
            throw new IllegalArgumentException("installerExe is null");
        }

        try {
            String appDir = System.getProperty("user.dir");
            Path exeToStart = Path.of(appDir, "PharmaX.exe");
            Path vbs = Path.of(appDir, "PharmaX.vbs");
            Path restartTarget = Files.exists(exeToStart) ? exeToStart : vbs;

            String silentArgs = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /FORCECLOSEAPPLICATIONS /RESTARTAPPLICATIONS";

            String batName = "PharmaX_update_" + UUID.randomUUID() + ".bat";
            Path bat = Path.of(System.getProperty("java.io.tmpdir"), batName);

            // Get current Java process ID to kill it specifically
            long pid = ProcessHandle.current().pid();

            String script = "@echo off\r\n"
                    + "setlocal\r\n"
                    + "echo Waiting for PharmaX to close...\r\n"
                    + "timeout /t 2 /nobreak >nul\r\n"
                    + "taskkill /F /IM PharmaX.exe >nul 2>&1\r\n"
                    + "taskkill /F /PID " + pid + " >nul 2>&1\r\n"
                    + "timeout /t 3 /nobreak >nul\r\n"
                    + "echo Starting update installer...\r\n"
                    + "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process -FilePath '" + escapeForPowerShell(installerExe.toString()) + "' -ArgumentList '" + silentArgs + "' -Verb RunAs -Wait\"\r\n"
                    + "echo Update completed. Restarting application...\r\n"
                    + "timeout /t 2 /nobreak >nul\r\n"
                    + "if exist \"" + restartTarget.toString() + "\" (\r\n"
                    + "  start \"\" \"" + restartTarget.toString() + "\"\r\n"
                    + ")\r\n"
                    + "del \"%~f0\"\r\n";

            Files.writeString(bat, script, StandardCharsets.UTF_8);

            new ProcessBuilder("cmd.exe", "/c", "start", "/min", bat.toString())
                    .inheritIO()
                    .start();

            logger.info("Update script started, application will close now");
            
            // Exit the application to allow update
            System.exit(0);

        } catch (IOException e) {
            logger.error("Failed to launch installer", e);
            throw new RuntimeException(e);
        }
    }

    public static void launchInstaller(Path installerExe) {
        // Delegate to launchInstallerAndRestart for automatic update
        launchInstallerAndRestart(installerExe);
    }

    private static String escapeForPowerShell(String s) {
        return s.replace("'", "''");
    }
}
