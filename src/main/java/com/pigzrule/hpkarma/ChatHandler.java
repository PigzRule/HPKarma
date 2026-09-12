package com.pigzrule.hpkarma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HPKarma ChatHandler:
 * Tailored for Hallow Prison with multi-category event stacking,
 * sequential human-paced dispatch queue, ReDoS-safe word boundary checks,
 * and NoChatReports / ChatScreen compatibility.
 */
public class ChatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("HPKarma");
    private static final Random RANDOM = new Random();

    // Event categories for distinct Hallow Prison wave tracking
    public enum EventCategory {
        ORANGE_GG,  // Crates, Milestones (§6 / Orange / Gold)
        CYAN_GG,    // Rebirths (Multi-colored / Cyan / Red / Pink)
        YELLOW_GG,  // Special milestones (§e / Yellow)
        PURPLE_GG,  // Rare items / Rankups (§d, §5 / Purple / Pink)
        RED_GG,     // Server boss / alerts (§c, §4 / Red)
        GREEN_GG,   // Other green gg (§a, §2 / Green)
        GENERAL_GG, // Fallback colored gg
        WELCOME     // New player joins
    }

    // User-configurable toggles
    public static volatile boolean enabled = true;
    public static volatile boolean ggEnabled = true;
    public static volatile boolean welcomeEnabled = true;
    public static volatile boolean serverLock = true;
    public static volatile boolean pauseWhenUnfocused = false;
    public static volatile boolean hudNotification = true;
    public static volatile boolean soundNotification = true;

    // Session stats tracking
    public static final java.util.concurrent.atomic.AtomicInteger SESSION_GGS = new java.util.concurrent.atomic.AtomicInteger(0);
    public static final java.util.concurrent.atomic.AtomicInteger SESSION_WELCOMES = new java.util.concurrent.atomic.AtomicInteger(0);
    public static final java.util.concurrent.atomic.AtomicInteger SESSION_VOTES = new java.util.concurrent.atomic.AtomicInteger(0);
    public static final java.util.concurrent.atomic.AtomicInteger SESSION_VOTE_KARMA = new java.util.concurrent.atomic.AtomicInteger(0);

    // Configurable estimated Karma per GG/Welcome response (defaults to base 25; dynamically updated by PerkManager)
    public static volatile int karmaPerGg = 25;

    public static int getGoodMannersBonusPct(int level) {
        return PerkManager.getGoodMannersBonusPct(level);
    }

    public static int calculateKarmaPerGg(int level) {
        return PerkManager.calculateKarmaPerGg(level);
    }

    public static void setGoodMannersLevel(int level) {
        PerkManager.setGoodMannersLevel(level);
    }

    // Exact phrase responses required by Hallow Prison's karma plugin
    public static String getGgResponse() {
        return "gg";
    }

    public static String getWelcomeResponse() {
        return "welcome";
    }

    /**
     * Checks whether the client is currently connected to Hallow Prison.
     * Supports primary domains, server list entry names, and scoreboard objective titles (for numeric IPs).
     */
    public static boolean isHallowPrisonConnected() {
        try {
            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client == null) return false;
            if (client.method_1496()) return false; // Singleplayer integrated server

            // 1. Direct Server Address & Server Entry Name Check
            net.minecraft.class_642 server = client.method_1558();
            if (server != null) {
                String address = server.field_3761;
                if (address != null) {
                    String lowerAddr = address.toLowerCase();
                    if (lowerAddr.contains("hallowprison") || lowerAddr.contains("hallow.gg") || lowerAddr.contains("hallow.fun") || lowerAddr.contains("hallow")) {
                        return true;
                    }
                }
                String serverName = server.field_3752;
                if (serverName != null) {
                    String lowerName = serverName.toLowerCase();
                    if (lowerName.contains("hallowprison") || lowerName.contains("hallow prison") || lowerName.contains("hallow")) {
                        return true;
                    }
                }
            }

            // 2. Scoreboard Sidebar & Objective Title Check (handles numeric IP or proxy connections)
            if (client.field_1687 != null) {
                net.minecraft.class_269 scoreboard = client.field_1687.method_8428();
                if (scoreboard != null) {
                    for (net.minecraft.class_266 objective : scoreboard.method_1151()) {
                        if (objective != null) {
                            net.minecraft.class_2561 displayName = objective.method_1114();
                            if (displayName != null) {
                                String title = displayName.getString().toLowerCase();
                                if (title.contains("hallow prison") || title.contains("hallowprison")) {
                                    return true;
                                }
                            }
                            String objName = objective.method_1113();
                            if (objName != null && (objName.toLowerCase().contains("hallowprison") || objName.toLowerCase().contains("hallow"))) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    // Humanized reaction + typing delays (milliseconds)
    public static volatile int minDelayGg = 1800;
    public static volatile int maxDelayGg = 3200;

    public static volatile int minDelayWelcome = 2400;
    public static volatile int maxDelayWelcome = 4500;

    // Wave lockout (milliseconds)
    public static volatile int waveCooldownMs = 5000;      // 5s lockout per category wave
    public static volatile int globalCooldownMs = 2500;    // 2.5s minimum spacing between consecutive packets
    public static volatile int triggerCooldownMs = 12000;  // Isolated trigger cooldown (ms)
    public static volatile int selfEventLockoutMs = 4000;  // 4s brief lockout after self-event (shortened so close subsequent events are not missed)

    // Per-category wave and activity tracking
    private static final ConcurrentHashMap<EventCategory, Long> LAST_WAVE_SENT_BY_CATEGORY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EventCategory, Long> LAST_SELF_EVENT_BY_CATEGORY = new ConcurrentHashMap<>();

    private static final AtomicLong NEXT_AVAILABLE_SEND_TIME = new AtomicLong(0);
    private static final AtomicLong LAST_ACTUAL_SENT_TIME = new AtomicLong(0);
    private static volatile String cachedPlayerName = null;

    /**
     * Safely extract the profile name across Authlib versions (record name() vs legacy getName()).
     */
    public static String getProfileName(Object profile) {
        if (profile == null) return null;
        try {
            Method nameMethod = profile.getClass().getMethod("name");
            return (String) nameMethod.invoke(profile);
        } catch (NoSuchMethodException ignored) {
            try {
                Method getNameMethod = profile.getClass().getMethod("getName");
                return (String) getNameMethod.invoke(profile);
            } catch (Throwable fallbackErr) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isConnectionOpen(Object conn) {
        if (conn == null) return false;
        try {
            Method m = conn.getClass().getMethod("method_10758");
            return Boolean.TRUE.equals(m.invoke(conn));
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * ReDoS-safe, linear-time word boundary check (matches 'gg', 'gg!', '[gg]', but not 'nuggets').
     */
    public static boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        String lower = text.toLowerCase();
        String target = word.toLowerCase();
        int idx = 0;
        int wordLen = target.length();
        int totalLen = lower.length();

        while ((idx = lower.indexOf(target, idx)) != -1) {
            boolean startOk = (idx == 0) || !Character.isLetterOrDigit(lower.charAt(idx - 1));
            int endIdx = idx + wordLen;
            boolean endOk = (endIdx == totalLen) || !Character.isLetterOrDigit(lower.charAt(endIdx));
            if (startOk && endOk) {
                return true;
            }
            idx += wordLen;
        }
        return false;
    }

    private static final java.util.regex.Pattern KARMA_GAIN_PATTERN = java.util.regex.Pattern.compile("(?i)(?:\\+(\\d+)\\s*karma|received\\s*(\\d+)\\s*karma)");

    /**
     * Extracts numerical karma gain from server notification strings.
     * Supports vote notices ("+1 Karma", "+2 Karma") and reward notices ("received 25 Karma").
     */
    public static int extractKarmaGain(String text) {
        if (text == null || text.isEmpty()) return 0;
        java.util.regex.Matcher m = KARMA_GAIN_PATTERN.matcher(text);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            String numStr = (g1 != null) ? g1 : g2;
            if (numStr != null) {
                try {
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static final java.util.regex.Pattern GOOD_MANNERS_UPGRADE_PATTERN =
            java.util.regex.Pattern.compile("(?i)upgraded\\s+good\\s+manners\\s+to\\s+([IVXLCDM0-9]+)");

    /**
     * Extracts Good Manners perk level from server upgrade notices.
     * e.g. "SUCCESS! Upgraded Good Manners to I." -> returns 1
     */
    public static int extractGoodMannersUpgrade(String text) {
        if (text == null || text.isEmpty()) return -1;
        java.util.regex.Matcher m = GOOD_MANNERS_UPGRADE_PATTERN.matcher(text);
        if (m.find()) {
            String token = m.group(1).trim().toUpperCase();
            switch (token) {
                case "I": case "1": return 1;
                case "II": case "2": return 2;
                case "III": case "3": return 3;
                case "IV": case "4": return 4;
                case "V": case "5": return 5;
                default:
                    try {
                        return Integer.parseInt(token);
                    } catch (Exception ignored) {
                        return -1;
                    }
            }
        }
        return -1;
    }

    /**
     * Detects if an announcement is related to a webstore / donation purchase.
     */
    public static boolean isStoreMessage(String lowerText) {
        if (lowerText == null) return false;
        return lowerText.contains("store") ||
               lowerText.contains("purchased") ||
               lowerText.contains("bought") ||
               lowerText.contains("shop") ||
               lowerText.contains("webstore") ||
               lowerText.contains("tebex") ||
               lowerText.contains("buycraft") ||
               lowerText.contains("donation") ||
               lowerText.contains("package") ||
               lowerText.contains("top customer") ||
               lowerText.contains("order");
    }

    /**
     * Categorizes a colored GG into an EventCategory based on style RGB and formatting codes.
     */
    public static EventCategory categorizeGg(net.minecraft.class_2583 style, String str) {
        if (style != null && style.method_10973() != null) {
            net.minecraft.class_5251 tc = style.method_10973();
            int rgb = tc.method_27716();
            String name = tc.method_27721();

            if (name != null) {
                String n = name.toLowerCase();
                if (n.contains("gold") || n.contains("orange")) return EventCategory.ORANGE_GG;
                if (n.contains("aqua") || n.contains("cyan") || n.contains("blue")) return EventCategory.CYAN_GG;
                if (n.contains("yellow")) return EventCategory.YELLOW_GG;
                if (n.contains("purple") || n.contains("pink") || n.contains("magenta") || n.contains("dark_purple")) return EventCategory.PURPLE_GG;
                if (n.contains("red")) return EventCategory.RED_GG;
                if (n.contains("green") || n.contains("lime")) return EventCategory.GREEN_GG;
            }

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Cyan / Aqua / Multi-colored: high green and blue (Hallow Prison Rebirths)
            if (b > 150 && g > 150 && (r < 190 || (b > r && g > r))) return EventCategory.CYAN_GG;
            // Orange / Gold: high red, medium green, low blue (Hallow Prison Crates / Purchases)
            if (r > 180 && g >= 70 && g <= 210 && b < 100) return EventCategory.ORANGE_GG;
            // Yellow: high red and green, low blue
            if (r > 180 && g > 180 && b < 100) return EventCategory.YELLOW_GG;
            // Purple / Pink: high red and blue, low green
            if (r > 130 && b > 130 && g < 130) return EventCategory.PURPLE_GG;
            // Red: high red, low green and blue
            if (r > 150 && g < 90 && b < 90) return EventCategory.RED_GG;
            // Green: high green
            if (g > 140 && r < 140 && b < 140) return EventCategory.GREEN_GG;
        }

        if (str != null) {
            int len = str.length();
            for (int i = 0; i < len - 1; i++) {
                if (str.charAt(i) == '\u00a7') {
                    char c = Character.toLowerCase(str.charAt(i + 1));
                    if (c == '6') return EventCategory.ORANGE_GG;
                    if (c == 'b' || c == '3' || c == '9' || c == '1') return EventCategory.CYAN_GG;
                    if (c == 'e') return EventCategory.YELLOW_GG;
                    if (c == 'd' || c == '5') return EventCategory.PURPLE_GG;
                    if (c == 'c' || c == '4') return EventCategory.RED_GG;
                    if (c == 'a' || c == '2') return EventCategory.GREEN_GG;
                }
            }
        }

        return EventCategory.GENERAL_GG;
    }

    /**
     * Checks if a message is a private message / whisper (/r, /msg, /tell, /w).
     */
    public static boolean isPrivateMessage(String lowerFull) {
        if (lowerFull == null) return false;
        return lowerFull.contains("\u2709") || // ✉ envelope icon used on Hallow Prison for PMs
               lowerFull.contains("you \u2192") || // YOU →
               lowerFull.contains("\u2192 you") || // → YOU
               lowerFull.contains("you ->") ||
               lowerFull.contains("-> you") ||
               lowerFull.contains("whispers to you") ||
               lowerFull.contains("you whisper to") ||
               lowerFull.contains("whisper:") ||
               lowerFull.startsWith("[msg]") ||
               lowerFull.startsWith("[pm]") ||
               lowerFull.startsWith("[tell]") ||
               lowerFull.startsWith("[w]");
    }

    /**
     * Checks if a message was sent by the local player.
     */
    public static boolean isOutgoingLocalMessage(String lowerFull, String senderName, String myName) {
        if (senderName != null && myName != null && senderName.equalsIgnoreCase(myName)) {
            return true;
        }
        if (lowerFull.contains("\u2709 you \u2192") || lowerFull.contains("you \u2192") || lowerFull.contains("you ->")) {
            return true;
        }
        if (myName != null && !myName.isEmpty()) {
            String lowerName = myName.toLowerCase();
            if (lowerFull.startsWith(lowerName + ":") ||
                lowerFull.contains(" " + lowerName + ":") ||
                lowerFull.contains("[" + lowerName + "]") ||
                lowerFull.contains(lowerName + " \u00bb") ||
                lowerFull.contains(lowerName + " >")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a message is a personal system notification directed only to the local player.
     * Excludes economy transactions, mine level-up prompts (e.g. INFO! You can now rebirth), and teleport notices.
     */
    public static boolean isPersonalNotification(String lower) {
        if (lower == null) return false;
        return lower.startsWith("info!") ||
               lower.startsWith("success!") ||
               lower.startsWith("error!") ||
               lower.startsWith("warning!") ||
               lower.startsWith("[!]") ||
               lower.contains("you can now rebirth") ||
               lower.contains("head to the rebirth station") ||
               lower.contains("cost you") ||
               lower.contains("may rebirth right now") ||
               lower.contains("teleported to rebirth") ||
               lower.contains("teleported to") ||
               lower.contains("you sold") ||
               lower.contains("you have opened") ||
               lower.contains("you have received") ||
               lower.contains("just won +") ||
               lower.contains("at the afk zone") ||
               lower.contains("the blacksmith has") ||
               lower.contains("you enchanted") ||
               lower.contains("just enchanted");
    }

    /**
     * Detects if a message originated from an individual player chatting (e.g. "[rank] Player: gg").
     * Paper/Spigot dispatch player chat as system messages to format custom rank badges and colors.
     */
    public static boolean isPlayerChatMessage(String fullString, String senderName) {
        if (senderName != null && !senderName.isEmpty()) return true;
        if (fullString == null || fullString.isEmpty()) return false;

        int colonIdx = fullString.indexOf(": ");
        if (colonIdx > 0) {
            String prefix = fullString.substring(0, colonIdx).trim();
            int lastSpace = prefix.lastIndexOf(' ');
            String possibleName = (lastSpace >= 0) ? prefix.substring(lastSpace + 1) : prefix;
            if (possibleName.endsWith("]")) {
                int lastBracket = prefix.lastIndexOf('[');
                if (lastBracket >= 0) {
                    possibleName = prefix.substring(lastBracket + 1, prefix.length() - 1);
                }
            }
            if (possibleName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                return true;
            }
        }
        return false;
    }

    public static void handleIncoming(net.minecraft.class_2561 text, String senderName) {
        handleIncoming(text, senderName, false);
    }

    public static void handleIncoming(net.minecraft.class_2561 text, String senderName, boolean isOverlay) {
        try {
            if (!enabled) return;
            if (isOverlay) return;

            // 0. Server Lock Guard: Only trigger when connected to Hallow Prison
            if (serverLock && !isHallowPrisonConnected()) {
                return;
            }

            // 0b. Focus / AFK Guard: Suppress when window is unfocused / tabbed out
            if (pauseWhenUnfocused) {
                try {
                    net.minecraft.class_310 clientTest = net.minecraft.class_310.method_1551();
                    if (clientTest != null && !clientTest.method_1569()) {
                        return;
                    }
                } catch (Throwable ignored) {}
            }

            long now = System.currentTimeMillis();

            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client == null) return;

            // Identify local player to prevent self-triggering
            String myName = null;
            try {
                if (client.method_1548() != null) {
                    myName = client.method_1548().method_1676();
                }
                if ((myName == null || myName.isEmpty()) && client.field_1724 != null) {
                    myName = client.field_1724.method_5820();
                }
                if ((myName == null || myName.isEmpty()) && client.field_1724 != null) {
                    myName = client.field_1724.method_5477().getString();
                }
                if (myName != null && !myName.isEmpty()) {
                    cachedPlayerName = myName;
                } else {
                    myName = cachedPlayerName;
                }
            } catch (Throwable ignored) {
                if (myName == null) myName = cachedPlayerName;
            }

            String fullString = (text != null) ? text.getString() : "";
            if (fullString == null || fullString.isEmpty()) {
                return;
            }

            String lowerFull = fullString.toLowerCase();

            // 1. Completely ignore all private messages and whispers (/r, /msg, /tell, /w)
            if (isPrivateMessage(lowerFull)) {
                return;
            }

            // 2. Completely ignore messages sent by the local player
            if (isOutgoingLocalMessage(lowerFull, senderName, myName)) {
                return;
            }

            // 3. Completely ignore all individual player chat messages (e.g. "[rank] Player: gg")
            // This prevents bot-baiting and prevents HPKarma from treating another player saying "gg" as a server event
            if (isPlayerChatMessage(fullString, senderName)) {
                return;
            }

            // 4a. Check for dynamic Vote / AFK / Reward Karma notifications
            // e.g. "❤ Thank you for voting PigzRule! +1 Karma  (/vote)"
            // or "SUCCESS! You tipped Huge Pets and received 25 Karma !"
            int karmaGained = extractKarmaGain(fullString);
            if (karmaGained > 0 && !lowerFull.contains("balance")) {
                SESSION_VOTE_KARMA.addAndGet(karmaGained);
                if (lowerFull.contains("voting") || lowerFull.contains("voted")) {
                    SESSION_VOTES.incrementAndGet();
                }
                LOGGER.info("[HPKarma] Recorded +{} Karma from '{}'", karmaGained, fullString);
                return;
            }

            // 4b. Check for automatic perk upgrades from server chat
            // e.g. "SUCCESS! Upgraded Good Manners to I." or "SUCCESS! Upgraded First Ballot to II."
            if (PerkManager.handleChatUpgrade(fullString)) {
                return;
            }

            // 4c. Completely ignore personal system notifications directed only to local player
            // (e.g. level 100 reminder: "INFO! You can now rebirth!", sell notices, crate opening recaps)
            if (isPersonalNotification(lowerFull)) {
                return;
            }

            // 5. Completely ignore all Store Purchases / TY requests (Production mode: Store TY disabled)
            if (isStoreMessage(lowerFull) ||
                lowerFull.contains("say ty in the chat") ||
                lowerFull.contains("say ty in chat") ||
                lowerFull.contains("say ty") ||
                lowerFull.contains("say 'ty'")) {
                LOGGER.debug("[HPKarma] Ignored store announcement (Production mode: Store TY disabled)");
                return;
            }

            EventCategory detectedGgCategory = null;
            boolean foundWelcome = false;

            // 6. Strict Server Announcement Matching:
            // Genuine Hallow Prison Rebirth Broadcasts:
            // e.g. " <Player> is now Rebirth XXVI, say gg in the chat!"
            if (lowerFull.contains("is now rebirth") ||
               (lowerFull.contains("rebirth") && (lowerFull.contains("say gg") || lowerFull.contains("say 'gg'")))) {
                if (lowerFull.contains("milestone")) {
                    detectedGgCategory = EventCategory.ORANGE_GG;
                } else {
                    detectedGgCategory = EventCategory.CYAN_GG;
                }
            } else if (lowerFull.contains("milestone") && (lowerFull.contains("say gg") || lowerFull.contains("say 'gg'"))) {
                detectedGgCategory = EventCategory.ORANGE_GG;
            } else if (lowerFull.contains("say gg in the chat") || lowerFull.contains("say gg in chat") || lowerFull.contains("say gg") || lowerFull.contains("say 'gg'")) {
                detectedGgCategory = EventCategory.CYAN_GG;
            }

            // Genuine Hallow Prison New Player Welcome Broadcasts:
            // e.g. "👋 <Player> joined for the first time, say welcome in the chat!"
            if (lowerFull.contains("joined for the first time") ||
                lowerFull.contains("say welcome in the chat") ||
                lowerFull.contains("say welcome in chat") ||
                lowerFull.contains("say welcome") ||
                lowerFull.contains("say 'welcome'")) {
                foundWelcome = true;
            }

            // 7. Self-Event Protection: If the announcement involves the local player,
            // suppress the auto-response AND apply a brief lockout so subsequent close messages from other players can still be congratulated!
            boolean isSelfAnnouncement = false;
            if (myName != null && !myName.isEmpty()) {
                if (containsWord(lowerFull, myName)) {
                    isSelfAnnouncement = true;
                } else {
                    String lowerName = myName.toLowerCase();
                    if (lowerFull.contains(" " + lowerName + " ") ||
                        lowerFull.startsWith(lowerName + " ") ||
                        lowerFull.contains(" " + lowerName + ",") ||
                        lowerFull.contains(" " + lowerName + "!") ||
                        lowerFull.contains("[" + lowerName + "]")) {
                        isSelfAnnouncement = true;
                    }
                }
            }

            if (isSelfAnnouncement) {
                if (detectedGgCategory != null || foundWelcome) {
                    LOGGER.info("[HPKarma] Announcement involves local player ('{}'); suppressing auto-response and applying brief {}s lockout.",
                            myName, selfEventLockoutMs / 1000);
                    if (detectedGgCategory != null) {
                        LAST_SELF_EVENT_BY_CATEGORY.put(detectedGgCategory, now);
                    }
                    if (foundWelcome) {
                        LAST_SELF_EVENT_BY_CATEGORY.put(EventCategory.WELCOME, now);
                    }
                    return;
                }
            }

            // 8. Schedule Rebirth / Milestone "gg" Response
            if (detectedGgCategory != null && ggEnabled) {
                Long lastSelf = LAST_SELF_EVENT_BY_CATEGORY.get(detectedGgCategory);
                if (selfEventLockoutMs > 0 && lastSelf != null && (now - lastSelf < selfEventLockoutMs)) {
                    long elapsedSec = (now - lastSelf) / 1000;
                    LOGGER.info("[HPKarma] Suppressed {} - brief self-event lockout active ({}s elapsed, lockout: {}s)",
                            detectedGgCategory, elapsedSec, selfEventLockoutMs / 1000);
                    return;
                }

                LOGGER.info("[HPKarma] Detected genuine GG announcement ({}) from text: '{}'", detectedGgCategory, fullString);
                int delay = minDelayGg + RANDOM.nextInt(Math.max(1, maxDelayGg - minDelayGg + 1));
                scheduleCategoryResponse("gg", detectedGgCategory, delay);
            }

            // 9. Schedule New Player "welcome" Response
            if (foundWelcome && welcomeEnabled) {
                Long lastSelf = LAST_SELF_EVENT_BY_CATEGORY.get(EventCategory.WELCOME);
                if (selfEventLockoutMs > 0 && lastSelf != null && (now - lastSelf < selfEventLockoutMs)) {
                    long elapsedSec = (now - lastSelf) / 1000;
                    LOGGER.info("[HPKarma] Suppressed Welcome - brief self-event lockout active ({}s elapsed, lockout: {}s)",
                            elapsedSec, selfEventLockoutMs / 1000);
                    return;
                }

                LOGGER.info("[HPKarma] Detected genuine Welcome announcement from text: '{}'", fullString);
                int delay = minDelayWelcome + RANDOM.nextInt(Math.max(1, maxDelayWelcome - minDelayWelcome + 1));
                scheduleCategoryResponse("welcome", EventCategory.WELCOME, delay);
            }
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Error processing chat event", t);
        }
    }

    /**
     * Schedules an event response through a sequential dispatch queue.
     * Guarantees:
     * 1. At most ONE response per event category per wave.
     * 2. Multiple different categories (e.g. Orange GG + Cyan GG + Welcome) stack naturally.
     * 3. Consecutive messages maintain a human typing interval (2.5s - 3.8s) so they never flood.
     */
    private static synchronized void scheduleCategoryResponse(String responseText, EventCategory category, int reactionDelayMs) {
        long now = System.currentTimeMillis();

        // 1. Wave Lockout Rule for this specific category
        Long lastSent = LAST_WAVE_SENT_BY_CATEGORY.get(category);
        if (lastSent != null) {
            long elapsedSinceSent = now - lastSent;
            if (elapsedSinceSent < waveCooldownMs) {
                LOGGER.info("[HPKarma] Suppressed {} - category wave lockout active ({}s elapsed, cooldown: {}s)",
                        category, elapsedSinceSent / 1000, waveCooldownMs / 1000);
                return;
            }
        }

        // Lock this category for the upcoming wave
        LAST_WAVE_SENT_BY_CATEGORY.put(category, now);

        // 2. Sequential Slot Calculation:
        long prevAvailable = NEXT_AVAILABLE_SEND_TIME.get();
        long targetTime = Math.max(now + reactionDelayMs, prevAvailable);

        // Reserve next available slot: targetTime + 2.5s to 3.8s natural human typing interval
        long nextSlot = targetTime + 2500 + RANDOM.nextInt(1300);
        NEXT_AVAILABLE_SEND_TIME.set(nextSlot);

        final long triggerTimestamp = now;
        final Object initialConn;
        net.minecraft.class_310 clientInstance = net.minecraft.class_310.method_1551();
        if (clientInstance != null && clientInstance.method_1562() != null) {
            initialConn = clientInstance.method_1562().method_48296();
        } else {
            initialConn = null;
        }

        long delayMs = Math.max(0, targetTime - now);
        LOGGER.info("[HPKarma] Queued {} -> '{}' (Dispatch in {}ms, target: +{}ms)",
                category, responseText, delayMs, (targetTime - now));

        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                long elapsed = System.currentTimeMillis() - triggerTimestamp;
                if (elapsed > 9000) {
                    LOGGER.info("[HPKarma] Dropped stale response for {} (TTL 9s exceeded: {}ms elapsed)", category, elapsed);
                    return;
                }

                net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
                if (client == null) {
                    LOGGER.info("[HPKarma] Suppressed response for {}: MinecraftClient is null", category);
                    return;
                }

                boolean isFocused = false;
                try {
                    isFocused = client.method_1569();
                } catch (Throwable ignored) {}

                if (pauseWhenUnfocused && !isFocused) {
                    LOGGER.info("[HPKarma] Suppressed response for {}: Window is unfocused (Pause Tabbed Out enabled)", category);
                    return;
                }

                // If unfocused and pauseWhenUnfocused is false: dispatch directly from background thread
                // to bypass Dynamic FPS 0-FPS render freeze!
                if (!isFocused) {
                    dispatchDirect(client, category, initialConn, triggerTimestamp, false);
                } else {
                    client.execute(() -> {
                        dispatchDirect(client, category, initialConn, triggerTimestamp, true);
                    });
                }
            } catch (Throwable e) {
                LOGGER.error("[HPKarma] Error in delayed executor", e);
            }
        });
    }

    /**
     * Executes the final packet dispatch and session tracking.
     * Safe to run from both render thread (when active) and background thread (when tabbed out).
     */
    private static void dispatchDirect(net.minecraft.class_310 client, EventCategory category, Object initialConn, long triggerTimestamp, boolean onRenderThread) {
        try {
            if (!enabled) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Master switch is disabled", category);
                return;
            }
            if (serverLock && !isHallowPrisonConnected()) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Not connected to Hallow Prison (serverLock ON)", category);
                return;
            }
            if (client.field_1724 == null || client.field_1687 == null) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Player or world is null", category);
                return;
            }

            // Death Safety: Suppress if player is dead, zero health, or on DeathScreen
            if (client.field_1724.method_29504() || client.field_1724.method_6032() <= 0 || client.field_1755 instanceof net.minecraft.class_418) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Player is dead or on DeathScreen", category);
                return;
            }

            net.minecraft.class_634 netHandler = client.method_1562();
            if (netHandler == null) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: netHandler is null", category);
                return;
            }

            Object conn = netHandler.method_48296();
            if (initialConn != null && conn != initialConn) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Connection changed since trigger", category);
                return;
            }
            if (!isConnectionOpen(conn)) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Suppressed response for {}: Connection is closed", category);
                return;
            }

            // Stale check at dispatch time
            long dispatchElapsed = System.currentTimeMillis() - triggerTimestamp;
            if (dispatchElapsed > 9000) {
                LAST_WAVE_SENT_BY_CATEGORY.remove(category);
                LOGGER.info("[HPKarma] Dropped response for {} at dispatch (TTL 9s exceeded: {}ms elapsed)", category, dispatchElapsed);
                return;
            }

            String textToSend = (category == EventCategory.WELCOME) ? getWelcomeResponse() : getGgResponse();
            LAST_ACTUAL_SENT_TIME.set(System.currentTimeMillis());

            // Synchronize with NoChatReports
            try {
                Class<?> sss = Class.forName("com.aizistral.nochatreports.common.core.ServerSafetyState");
                sss.getMethod("setLastMessage", String.class).invoke(null, textToSend);
            } catch (Throwable ignored) {}

            // Send directly via netHandler
            netHandler.method_45729(textToSend);
            LOGGER.info("[HPKarma] Sent {} via netHandler: '{}' (focused: {}, thread: {})",
                    category, textToSend, client.method_1569(), onRenderThread ? "render" : "async");

            // Update session counters
            if (category == EventCategory.WELCOME) {
                SESSION_WELCOMES.incrementAndGet();
            } else {
                SESSION_GGS.incrementAndGet();
            }

            // Non-blocking actionbar HUD & sound feedback
            if (hudNotification || soundNotification) {
                client.execute(() -> {
                    try {
                        if (hudNotification && client.field_1724 != null) {
                            String hudMsg = "§8[§5§lH§6§lP§e§lKarma \uE17E§8] §7Auto-responded: §e" + textToSend + " §a(+" + karmaPerGg + " Karma)";
                            client.field_1724.method_7353(net.minecraft.class_2561.method_30163(hudMsg), true);
                        }
                        if (soundNotification && client.field_1724 != null) {
                            client.field_1724.method_5783(net.minecraft.class_3417.field_14627, 0.35F, 1.3F);
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable sendErr) {
            LOGGER.error("[HPKarma] Error sending response packet", sendErr);
        }
    }

    /**
     * Comprehensive color detection for any non-white, non-gray text.
     */
    private static boolean isColored(net.minecraft.class_2583 style, String str) {
        if (style != null && style.method_10973() != null) {
            net.minecraft.class_5251 tc = style.method_10973();
            int rgb = tc.method_27716();
            String name = tc.method_27721();

            if (rgb != 0xFFFFFF && rgb != 0xAAAAAA && rgb != 0x555555 && rgb != 0) {
                return true;
            }
            if (name != null) {
                String lowerName = name.toLowerCase();
                if (!lowerName.equals("white") && !lowerName.equals("gray") &&
                    !lowerName.equals("dark_gray") && !lowerName.equals("reset")) {
                    return true;
                }
            }
        }

        if (str != null) {
            int len = str.length();
            for (int i = 0; i < len - 1; i++) {
                if (str.charAt(i) == '\u00a7') {
                    char code = Character.toLowerCase(str.charAt(i + 1));
                    if ((code >= '0' && code <= '6') || (code >= '9' && code <= 'f') || code == 'x' || code == '#') {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Comprehensive green color variation detection.
     */
    private static boolean isGreen(net.minecraft.class_2583 style, String str) {
        if (style != null && style.method_10973() != null) {
            net.minecraft.class_5251 tc = style.method_10973();
            int rgb = tc.method_27716();
            String name = tc.method_27721();

            if (name != null) {
                String lowerName = name.toLowerCase();
                if (lowerName.contains("green") || lowerName.contains("lime") ||
                    lowerName.contains("emerald") || lowerName.contains("mint")) {
                    return true;
                }
            }
            if (rgb == 0x55FF55 || rgb == 0x00AA00) {
                return true;
            }
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            if (g >= 50 && g > r && g > b) {
                return true;
            }
        }

        if (str != null) {
            int len = str.length();
            for (int i = 0; i < len - 1; i++) {
                if (str.charAt(i) == '\u00a7') {
                    char code = Character.toLowerCase(str.charAt(i + 1));
                    if (code == '2' || code == 'a') {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
