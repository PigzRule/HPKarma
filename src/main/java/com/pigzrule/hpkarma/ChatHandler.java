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
        CYAN_GG,    // Rebirths, Karma (§b / Cyan / Aqua)
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
                    if (lowerAddr.contains("hallowprison.com") || lowerAddr.contains("hallowprison") || lowerAddr.contains("hallow.gg")) {
                        return true;
                    }
                }
                String serverName = server.field_3752;
                if (serverName != null) {
                    String lowerName = serverName.toLowerCase();
                    if (lowerName.contains("hallowprison") || lowerName.contains("hallow prison")) {
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

    // Wave lockout & quiet period (milliseconds)
    public static volatile int waveCooldownMs = 45000;     // 45s lockout per category wave
    public static volatile int waveQuietPeriodMs = 10000;  // 10s quiet break required before new category wave
    public static volatile int globalCooldownMs = 2500;    // 2.5s minimum spacing between consecutive packets
    public static volatile int triggerCooldownMs = 12000;  // Isolated trigger cooldown (ms)

    // Per-category wave and activity tracking
    private static final ConcurrentHashMap<EventCategory, Long> LAST_WAVE_SENT_BY_CATEGORY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EventCategory, Long> LAST_SEEN_BY_CATEGORY = new ConcurrentHashMap<>();

    // Sequential queue slot tracking for natural multi-type stacking
    private static final AtomicLong NEXT_AVAILABLE_SEND_TIME = new AtomicLong(0);
    private static final AtomicLong LAST_ACTUAL_SENT_TIME = new AtomicLong(0);

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
        if (text == null || word == null) return false;
        String lower = text.toLowerCase();
        int idx = 0;
        int wordLen = word.length();
        int totalLen = lower.length();

        while ((idx = lower.indexOf(word, idx)) != -1) {
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

            // Cyan / Aqua: high green and blue, low red (Hallow Prison Rebirths)
            if (b > 150 && g > 150 && r < 140) return EventCategory.CYAN_GG;
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

    public static void handleIncoming(net.minecraft.class_2561 text, String senderName) {
        handleIncoming(text, senderName, false);
    }

    public static void handleIncoming(net.minecraft.class_2561 text, String senderName, boolean isOverlay) {
        try {
            if (!enabled) return;

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
                if (myName == null && client.field_1724 != null) {
                    myName = client.field_1724.method_5477().getString();
                }
            } catch (Throwable ignored) {}

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

            // 3. Self-Announcement Guard: If the message mentions our username in an announcement context,
            // do not auto-respond (prevents "gg"ing your own rebirth or welcoming yourself)
            if (myName != null && !myName.isEmpty() && lowerFull.contains(myName.toLowerCase())) {
                if (senderName == null || isOverlay || lowerFull.contains("rebirth") || lowerFull.contains("joined for the first time")) {
                    LOGGER.debug("[HPKarma] Announcement involves local player (self-event); suppressing auto-response.");
                    return;
                }
            }

            if (isOverlay) {
                LOGGER.info("[HPKarma] Received overlay/actionbar message: {}", fullString);
            }

            // 4. Completely ignore all Store Purchases / TY requests (Production build complies with server anti-farming rules)
            if (isStoreMessage(lowerFull) ||
                lowerFull.contains("say ty in the chat") ||
                lowerFull.contains("say ty in chat") ||
                lowerFull.contains("say ty") ||
                lowerFull.contains("say 'ty'")) {
                LOGGER.debug("[HPKarma] Ignored store announcement (Production mode: Store TY disabled)");
                return;
            }

            // 5. Distinguish genuine server announcements from player chat to prevent bot-baiting
            boolean isServerAnnouncement = (senderName == null) || isOverlay;

            EventCategory detectedGgCategory = null;
            boolean foundWelcome = false;

            // Server announcements on Hallow Prison
            if (isServerAnnouncement) {
                if (lowerFull.contains("rebirth") || lowerFull.contains("rebirthed")) {
                    if (lowerFull.contains("milestone")) {
                        detectedGgCategory = EventCategory.ORANGE_GG;
                    } else {
                        detectedGgCategory = EventCategory.CYAN_GG;
                    }
                } else if (lowerFull.contains("milestone")) {
                    detectedGgCategory = EventCategory.ORANGE_GG;
                } else if (lowerFull.contains("say gg in the chat") || lowerFull.contains("say gg in chat") || lowerFull.contains("say gg") || lowerFull.contains("say 'gg'")) {
                    detectedGgCategory = EventCategory.CYAN_GG;
                }

                if (lowerFull.contains("say welcome in the chat") || lowerFull.contains("say welcome in chat") || lowerFull.contains("say welcome") || lowerFull.contains("joined for the first time")) {
                    foundWelcome = true;
                }
            }

            // 6. Styled text component traversal: inspects for colored 'gg', 'ggs', or green 'welcome' (server announcements only to prevent player baiting)
            if (isServerAnnouncement && text != null && (detectedGgCategory == null || !foundWelcome)) {
                final EventCategory[] compGgCat = new EventCategory[1];
                final boolean[] compWelcome = new boolean[1];

                text.method_27658((style, str) -> {
                    if (str != null && !str.isEmpty()) {
                        String sLower = str.toLowerCase();

                        // Check for 'gg' or 'ggs' as a whole word with its distinct color category
                        if (compGgCat[0] == null && (containsWord(sLower, "gg") || containsWord(sLower, "ggs"))) {
                            compGgCat[0] = categorizeGg(style, str);
                        }

                        // Check for 'welcome' as a whole word with green color variation
                        if (!compWelcome[0] && containsWord(sLower, "welcome") && isGreen(style, str)) {
                            compWelcome[0] = true;
                        }
                    }
                    return Optional.empty();
                }, net.minecraft.class_2583.field_24360);

                if (compGgCat[0] != null) detectedGgCategory = compGgCat[0];
                if (compWelcome[0]) foundWelcome = true;
            }

            // 1. Schedule Rebirth / Milestone "gg" Response
            if (detectedGgCategory != null && ggEnabled) {
                LOGGER.info("[HPKarma] Detected GG trigger ({}) from text: '{}'", detectedGgCategory, fullString);
                LAST_SEEN_BY_CATEGORY.put(detectedGgCategory, now);
                int delay = minDelayGg + RANDOM.nextInt(Math.max(1, maxDelayGg - minDelayGg + 1));
                scheduleCategoryResponse("gg", detectedGgCategory, delay);
            }

            // 2. Schedule New Player "welcome" Response
            if (foundWelcome && welcomeEnabled) {
                LOGGER.info("[HPKarma] Detected Welcome trigger from text: '{}'", fullString);
                LAST_SEEN_BY_CATEGORY.put(EventCategory.WELCOME, now);
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
                LOGGER.debug("[HPKarma] Suppressing {} - category wave lockout active ({}s elapsed)",
                        category, elapsedSinceSent / 1000);
                return;
            }

            // Quiet period: ensure chat had a break if wave previously ran
            Long lastSeen = LAST_SEEN_BY_CATEGORY.get(category);
            if (lastSeen != null && elapsedSinceSent < 90000 && (now - lastSeen < waveQuietPeriodMs)) {
                LOGGER.debug("[HPKarma] Suppressing {} - category wave still ongoing without quiet break", category);
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
                // Drop stale events if queuing or lag exceeded TTL (9.0s)
                if (System.currentTimeMillis() - triggerTimestamp > 9000) {
                    LOGGER.debug("[HPKarma] Dropped stale response for {} (TTL 9s exceeded)", category);
                    return;
                }

                net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
                if (client == null) return;

                client.execute(() -> {
                    try {
                        if (!enabled) return;
                        if (serverLock && !isHallowPrisonConnected()) return;
                        if (pauseWhenUnfocused && !client.method_1569()) return;
                        if (client.field_1724 == null || client.field_1687 == null) return;

                        // Container / HandledScreen Safety: Never chat while a chest, inventory, or shop GUI is open
                        if (client.field_1755 instanceof net.minecraft.class_465) {
                            LOGGER.debug("[HPKarma] HandledScreen (container/chest/GUI) is open; suppressing response for anti-cheat safety.");
                            return;
                        }

                        // Death Safety: Suppress if player is dead, zero health, or on DeathScreen
                        if (client.field_1724.method_29504() || client.field_1724.method_6032() <= 0 || client.field_1755 instanceof net.minecraft.class_418) {
                            LOGGER.debug("[HPKarma] Player is dead or on DeathScreen; suppressing response.");
                            return;
                        }

                        net.minecraft.class_634 netHandler = client.method_1562();
                        if (netHandler == null) return;

                        Object conn = netHandler.method_48296();
                        // Connection Affinity Check: Drop if connection changed (reconnected or switched servers)
                        if (initialConn != null && conn != initialConn) {
                            LOGGER.debug("[HPKarma] Connection changed since event trigger; dropping response.");
                            return;
                        }
                        if (!isConnectionOpen(conn)) return;

                        // Select text to send (randomized or default)
                        String textToSend;
                        if (category == EventCategory.WELCOME) {
                            textToSend = getWelcomeResponse();
                        } else {
                            textToSend = getGgResponse();
                        }

                        long dispatchNow = System.currentTimeMillis();
                        LAST_ACTUAL_SENT_TIME.set(dispatchNow);

                        // Synchronize with NoChatReports
                        try {
                            Class<?> sss = Class.forName("com.aizistral.nochatreports.common.core.ServerSafetyState");
                            sss.getMethod("setLastMessage", String.class).invoke(null, textToSend);
                        } catch (Throwable ignored) {}

                        // Send through network or ChatScreen pipeline without interrupting open screens
                        boolean sent = false;
                        if (client.field_1755 instanceof net.minecraft.class_408) {
                            // If player is actively typing in chat, sending via netHandler directly preserves their open screen and draft!
                            netHandler.method_45729(textToSend);
                            sent = true;
                            LOGGER.info("[HPKarma] Sent {} via netHandler (player typing draft preserved): '{}'", category, textToSend);
                        } else {
                            try {
                                net.minecraft.class_408 screen = new net.minecraft.class_408("", false);
                                net.minecraft.class_1041 win = client.method_22683();
                                if (win != null) {
                                    screen.method_25423(win.method_4486(), win.method_4502());
                                }
                                screen.method_44056(textToSend, false);
                                sent = true;
                                LOGGER.info("[HPKarma] Sent {} via ChatScreen pipeline: '{}'", category, textToSend);
                            } catch (Throwable screenErr) {
                                LOGGER.warn("[HPKarma] ChatScreen fallback to netHandler", screenErr);
                            }
                        }

                        if (!sent) {
                            netHandler.method_45729(textToSend);
                            LOGGER.info("[HPKarma] Sent {} via netHandler: '{}'", category, textToSend);
                        }

                        // Update session counters
                        if (category == EventCategory.WELCOME) {
                            SESSION_WELCOMES.incrementAndGet();
                        } else {
                            SESSION_GGS.incrementAndGet();
                        }

                        // Subtle action-bar HUD feedback
                        if (hudNotification && client.field_1724 != null) {
                            try {
                                String hudMsg = "§8[§5§lH§6§lP§e§lKarma \uE17E§8] §7Auto-responded: §e" + textToSend + " §a(+25 Karma)";
                                client.field_1724.method_7353(net.minecraft.class_2561.method_30163(hudMsg), true);
                            } catch (Throwable ignored) {}
                        }

                        // Subtle audio chime feedback
                        if (soundNotification && client.field_1724 != null) {
                            try {
                                client.field_1724.method_5783(net.minecraft.class_3417.field_14627, 0.35F, 1.3F);
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable sendErr) {
                        LOGGER.error("[HPKarma] Error sending packet to server", sendErr);
                    }
                });
            } catch (Throwable e) {
                LOGGER.error("[HPKarma] Error in delayed executor", e);
            }
        });
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
