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

            ChatHandler.enabled = parseBoolOrDefault(values, "enabled", ChatHandler.enabled);
            ChatHandler.ggEnabled = parseBoolOrDefault(values, "ggEnabled", ChatHandler.ggEnabled);
            ChatHandler.welcomeEnabled = parseBoolOrDefault(values, "welcomeEnabled", ChatHandler.welcomeEnabled);
            ChatHandler.serverLock = parseBoolOrDefault(values, "serverLock", ChatHandler.serverLock);
            ChatHandler.pauseWhenUnfocused = parseBoolOrDefault(values, "pauseWhenUnfocused", ChatHandler.pauseWhenUnfocused);
            ChatHandler.hudNotification = parseBoolOrDefault(values, "hudNotification", ChatHandler.hudNotification);
            ChatHandler.soundNotification = parseBoolOrDefault(values, "soundNotification", ChatHandler.soundNotification);

            ChatHandler.waveCooldownMs = parseIntOrDefault(values, "waveCooldownMs", ChatHandler.waveCooldownMs, 10000);
            ChatHandler.globalCooldownMs = parseIntOrDefault(values, "globalCooldownMs", ChatHandler.globalCooldownMs, 1000);
            ChatHandler.triggerCooldownMs = parseIntOrDefault(values, "triggerCooldownMs", ChatHandler.triggerCooldownMs, 4000);
            ChatHandler.selfEventLockoutMs = parseIntOrDefault(values, "selfEventLockoutMs", ChatHandler.selfEventLockoutMs, 0);

            ChatHandler.minDelayGg = parseIntOrDefault(values, "minDelayGg", ChatHandler.minDelayGg, 500);
            ChatHandler.maxDelayGg = Math.max(ChatHandler.minDelayGg, parseIntOrDefault(values, "maxDelayGg", ChatHandler.maxDelayGg, ChatHandler.minDelayGg));
            ChatHandler.minDelayWelcome = parseIntOrDefault(values, "minDelayWelcome", ChatHandler.minDelayWelcome, 500);
            ChatHandler.maxDelayWelcome = Math.max(ChatHandler.minDelayWelcome, parseIntOrDefault(values, "maxDelayWelcome", ChatHandler.maxDelayWelcome, ChatHandler.minDelayWelcome));

            PerkManager.goodMannersLevel = parseIntOrDefault(values, "goodMannersLevel", 0, 0);
            PerkManager.firstBallotLevel = parseIntOrDefault(values, "firstBallotLevel", 0, 0);
            PerkManager.rallyingCryLevel = parseIntOrDefault(values, "rallyingCryLevel", 0, 0);
            PerkManager.graciousThanksLevel = parseIntOrDefault(values, "graciousThanksLevel", 0, 0);

            PerkManager.setGoodMannersLevel(PerkManager.goodMannersLevel);
            if (!values.containsKey("goodMannersLevel") && values.containsKey("karmaPerGg")) {
                ChatHandler.karmaPerGg = parseIntOrDefault(values, "karmaPerGg", ChatHandler.karmaPerGg, 0);
            }

            LOGGER.info("[HPKarma] Configuration loaded from {}", path);
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Error loading configuration, using defaults", t);
        }
    }

    private static boolean parseBoolOrDefault(Map<String, String> values, String key, boolean defaultVal) {
        if (values != null && values.containsKey(key)) {
            return Boolean.parseBoolean(values.get(key));
        }
        return defaultVal;
    }

    private static int parseIntOrDefault(Map<String, String> values, String key, int defaultVal, int minVal) {
        if (values != null && values.containsKey(key)) {
            try {
                return Math.max(minVal, Integer.parseInt(values.get(key)));
            } catch (Exception e) {
                LOGGER.warn("[HPKarma] Invalid integer for key '{}': '{}'. Keeping default: {}", key, values.get(key), defaultVal);
            }
        }
        return defaultVal;
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
                writer.write("  \"selfEventLockoutMs\": " + ChatHandler.selfEventLockoutMs + ",\n");
                writer.write("  \"minDelayGg\": " + ChatHandler.minDelayGg + ",\n");
                writer.write("  \"maxDelayGg\": " + ChatHandler.maxDelayGg + ",\n");
                writer.write("  \"minDelayWelcome\": " + ChatHandler.minDelayWelcome + ",\n");
                writer.write("  \"maxDelayWelcome\": " + ChatHandler.maxDelayWelcome + ",\n");
                writer.write("  \"karmaPerGg\": " + ChatHandler.karmaPerGg + ",\n");
                writer.write("  \"goodMannersLevel\": " + PerkManager.goodMannersLevel + ",\n");
                writer.write("  \"firstBallotLevel\": " + PerkManager.firstBallotLevel + ",\n");
                writer.write("  \"rallyingCryLevel\": " + PerkManager.rallyingCryLevel + ",\n");
                writer.write("  \"graciousThanksLevel\": " + PerkManager.graciousThanksLevel + "\n");
                writer.write("}\n");
            }

            LOGGER.debug("[HPKarma] Configuration saved to {}", path);
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Error saving configuration", t);
        }
    }
}
