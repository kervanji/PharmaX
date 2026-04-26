package com.pharmax.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfigStore {
    private static final Logger logger = LoggerFactory.getLogger(AppConfigStore.class);

    private static final String CONFIG_FILE = "app_config.properties";

    public Properties load() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                logger.error("Failed to load config file", e);
            }
        }
        return props;
    }

    public void save(Properties props) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "PharmaX Application Configuration");
        } catch (IOException e) {
            logger.error("Failed to save config file", e);
        }
    }
}
