package com.pigzrule.hpkarma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON configuration manager for HPKarma.
 * Persists user toggles and cooldown settings across game restarts.
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("HPKarma");
    private static final String FILE_NAME = "hp-karma.json";

    public static Path getConfigPath() {
        try {
            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client != null && client.field_1697 != null) {
                return client.field_1697.toPath().resolve("config").resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {}
        return Paths.get("config", FILE_NAME);
    }

    public static synchronized void load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                save(); // Write default configuration
                return;
            }

            Map<String, String> values = new HashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("{") || line.startsWith("}") || line.isEmpty() || line.startsWith("//")) {
                        continue;
                    }
                    int colonIdx = line.indexOf(':');
                    if (colonIdx != -1) {
                        String key = line.substring(0, colonIdx).replace("\"", "").trim();
                        String val = line.substring(colonIdx + 1).replace("\"", "").replace(",", "").trim();
                        values.put(key, val);
                    }
                }
            }

            if (values.containsKey("enabled")) {
                ChatHandler.enabled = Boolean.parseBoolean(values.get("enabled"));
            }
            if (values.containsKey("ggEnabled")) {
                ChatHandler.ggEnabled = Boolean.parseBoolean(values.get("ggEnabled"));
            }
            if (values.containsKey("welcomeEnabled")) {
                ChatHandler.welcomeEnabled = Boolean.parseBoolean(values.get("welcomeEnabled"));
            }
            if (values.containsKey("serverLock")) {
                ChatHandler.serverLock = Boolean.parseBoolean(values.get("serverLock"));
            }
            if (values.containsKey("pauseWhenUnfocused")) {
                ChatHandler.pauseWhenUnfocused = Boolean.parseBoolean(values.get("pauseWhenUnfocused"));
            }
            if (values.containsKey("hudNotification")) {
                ChatHandler.hudNotification = Boolean.parseBoolean(values.get("hudNotification"));
            }
            if (values.containsKey("soundNotification")) {
                ChatHandler.soundNotification = Boolean.parseBoolean(values.get("soundNotification"));
            }
            if (values.containsKey("waveCooldownMs")) {
                ChatHandler.waveCooldownMs = Math.max(10000, Integer.parseInt(values.get("waveCooldownMs")));
            }
            if (values.containsKey("globalCooldownMs")) {
                ChatHandler.globalCooldownMs = Math.max(1000, Integer.parseInt(values.get("globalCooldownMs")));
            }
            if (values.containsKey("triggerCooldownMs")) {
                ChatHandler.triggerCooldownMs = Math.max(4000, Integer.parseInt(values.get("triggerCooldownMs")));
            }
            if (values.containsKey("minDelayGg")) {
                ChatHandler.minDelayGg = Math.max(500, Integer.parseInt(values.get("minDelayGg")));
            }
            if (values.containsKey("maxDelayGg")) {
                ChatHandler.maxDelayGg = Math.max(ChatHandler.minDelayGg, Integer.parseInt(values.get("maxDelayGg")));
            }
            if (values.containsKey("minDelayWelcome")) {
                ChatHandler.minDelayWelcome = Math.max(500, Integer.parseInt(values.get("minDelayWelcome")));
            }
            if (values.containsKey("maxDelayWelcome")) {
                ChatHandler.maxDelayWelcome = Math.max(ChatHandler.minDelayWelcome, Integer.parseInt(values.get("maxDelayWelcome")));
            }

            LOGGER.info("[HPKarma] Configuration loaded from {}", path);
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Error loading configuration, using defaults", t);
        }
    }

    public static synchronized void save() {
        try {
            Path path = getConfigPath();
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("{\n");
                writer.write("  \"enabled\": " + ChatHandler.enabled + ",\n");
                writer.write("  \"ggEnabled\": " + ChatHandler.ggEnabled + ",\n");
                writer.write("  \"welcomeEnabled\": " + ChatHandler.welcomeEnabled + ",\n");
                writer.write("  \"serverLock\": " + ChatHandler.serverLock + ",\n");
                writer.write("  \"pauseWhenUnfocused\": " + ChatHandler.pauseWhenUnfocused + ",\n");
                writer.write("  \"hudNotification\": " + ChatHandler.hudNotification + ",\n");
                writer.write("  \"soundNotification\": " + ChatHandler.soundNotification + ",\n");
                writer.write("  \"waveCooldownMs\": " + ChatHandler.waveCooldownMs + ",\n");
                writer.write("  \"globalCooldownMs\": " + ChatHandler.globalCooldownMs + ",\n");
                writer.write("  \"triggerCooldownMs\": " + ChatHandler.triggerCooldownMs + ",\n");
                writer.write("  \"minDelayGg\": " + ChatHandler.minDelayGg + ",\n");
                writer.write("  \"maxDelayGg\": " + ChatHandler.maxDelayGg + ",\n");
                writer.write("  \"minDelayWelcome\": " + ChatHandler.minDelayWelcome + ",\n");
                writer.write("  \"maxDelayWelcome\": " + ChatHandler.maxDelayWelcome + "\n");
                writer.write("}\n");
            }

            LOGGER.debug("[HPKarma] Configuration saved to {}", path);
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Error saving configuration", t);
        }
    }
}
