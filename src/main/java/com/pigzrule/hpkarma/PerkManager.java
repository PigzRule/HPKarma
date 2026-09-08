package com.pigzrule.hpkarma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic Perk Detection & Interpretation Engine for Hallow Prison.
 * Detects perks automatically via:
 * 1. Server upgrade notices in chat (e.g. "SUCCESS! Upgraded Good Manners to I.")
 * 2. Container GUI scanning when opening the Vote or AFK perk trees.
 *
 * Fully automatic: zero manual commands or user configuration required.
 */
public class PerkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("HPKarma");

    // Auto-detected perk levels (defaults to base Tier 0 on clean install; auto-synced from GUI/chat)
    public static volatile int goodMannersLevel = 0;    // 0-5 (+12% to +60% GG/Welcome Karma -> 25 base)
    public static volatile int firstBallotLevel = 0;    // 0-5 (+10% to +50% Vote Karma)
    public static volatile int rallyingCryLevel = 0;    // 0-4 (+20% to +80% Vote Karma)
    public static volatile int graciousThanksLevel = 0; // 0-2 (+25% to +50% Store TY Gems)

    // Server perk boost tables
    public static final int BASE_GG_KARMA = 25;
    public static final int[] GOOD_MANNERS_BOOSTS = { 0, 12, 24, 36, 48, 60 };
    public static final int[] FIRST_BALLOT_BOOSTS = { 0, 10, 20, 30, 40, 50 };
    public static final int[] RALLYING_CRY_BOOSTS = { 0, 20, 40, 60, 80 };
    public static final int[] GRACIOUS_THANKS_BOOSTS = { 0, 25, 50 };

    public static int getGoodMannersBonusPct() {
        return getGoodMannersBonusPct(goodMannersLevel);
    }

    public static int getGoodMannersBonusPct(int level) {
        if (level <= 0) return 0;
        if (level >= GOOD_MANNERS_BOOSTS.length) return GOOD_MANNERS_BOOSTS[GOOD_MANNERS_BOOSTS.length - 1];
        return GOOD_MANNERS_BOOSTS[level];
    }

    public static int getFirstBallotBonusPct() {
        if (firstBallotLevel <= 0) return 0;
        if (firstBallotLevel >= FIRST_BALLOT_BOOSTS.length) return FIRST_BALLOT_BOOSTS[FIRST_BALLOT_BOOSTS.length - 1];
        return FIRST_BALLOT_BOOSTS[firstBallotLevel];
    }

    public static int getRallyingCryBonusPct() {
        if (rallyingCryLevel <= 0) return 0;
        if (rallyingCryLevel >= RALLYING_CRY_BOOSTS.length) return RALLYING_CRY_BOOSTS[RALLYING_CRY_BOOSTS.length - 1];
        return RALLYING_CRY_BOOSTS[rallyingCryLevel];
    }

    public static int getTotalVoteBonusPct() {
        return getFirstBallotBonusPct() + getRallyingCryBonusPct();
    }

    public static int getGraciousThanksBonusPct() {
        if (graciousThanksLevel <= 0) return 0;
        if (graciousThanksLevel >= GRACIOUS_THANKS_BOOSTS.length) return GRACIOUS_THANKS_BOOSTS[GRACIOUS_THANKS_BOOSTS.length - 1];
        return GRACIOUS_THANKS_BOOSTS[graciousThanksLevel];
    }

    public static int calculateKarmaPerGg() {
        return calculateKarmaPerGg(goodMannersLevel);
    }

    public static int calculateKarmaPerGg(int level) {
        int boost = getGoodMannersBonusPct(level);
        return (int) Math.round(BASE_GG_KARMA * (1.0 + (boost / 100.0)));
    }

    public static void setGoodMannersLevel(int level) {
        goodMannersLevel = Math.max(0, Math.min(5, level));
        ChatHandler.karmaPerGg = calculateKarmaPerGg(goodMannersLevel);
    }

    public static void setFirstBallotLevel(int level) {
        firstBallotLevel = Math.max(0, Math.min(5, level));
    }

    public static void setRallyingCryLevel(int level) {
        rallyingCryLevel = Math.max(0, Math.min(4, level));
    }

    public static void setGraciousThanksLevel(int level) {
        graciousThanksLevel = Math.max(0, Math.min(2, level));
    }

    public static int parseRomanOrInt(String token) {
        if (token == null) return -1;
        token = token.trim().toUpperCase();
        switch (token) {
            case "I": case "1": return 1;
            case "II": case "2": return 2;
            case "III": case "3": return 3;
            case "IV": case "4": return 4;
            case "V": case "5": return 5;
            default:
                try {
                    return Integer.parseInt(token);
                } catch (Exception e) {
                    return -1;
                }
        }
    }

    // Pattern for server upgrade messages (e.g. "SUCCESS! Upgraded Good Manners to I.")
    private static final Pattern PERK_UPGRADE_PATTERN =
            Pattern.compile("(?i)SUCCESS!\\s+Upgraded\\s+([A-Za-z\\s]+?)\\s+to\\s+([IVXLCDM0-9]+)\\.?");

    /**
     * Automatically handles chat upgrade notices for any known perk.
     * Returns true if the message was an upgrade notice.
     */
    public static boolean handleChatUpgrade(String message) {
        if (message == null || message.isEmpty()) return false;
        Matcher m = PERK_UPGRADE_PATTERN.matcher(message);
        if (!m.find()) return false;

        String perkName = m.group(1).trim().toLowerCase();
        String tierStr = m.group(2).trim().toUpperCase();
        int tier = parseRomanOrInt(tierStr);
        if (tier <= 0) return false;

        boolean updated = false;
        String detectedPerk = null;

        if (perkName.contains("good manners")) {
            setGoodMannersLevel(tier);
            detectedPerk = "Good Manners " + tierStr + " (+" + getGoodMannersBonusPct() + "% -> +" + ChatHandler.karmaPerGg + " Karma/GG)";
            updated = true;
        } else if (perkName.contains("first ballot")) {
            setFirstBallotLevel(tier);
            detectedPerk = "First Ballot " + tierStr + " (+" + getFirstBallotBonusPct() + "% Vote Karma)";
            updated = true;
        } else if (perkName.contains("rallying cry")) {
            setRallyingCryLevel(tier);
            detectedPerk = "Rallying Cry " + tierStr + " (+" + getRallyingCryBonusPct() + "% Vote Karma)";
            updated = true;
        } else if (perkName.contains("gracious thanks")) {
            setGraciousThanksLevel(tier);
            detectedPerk = "Gracious Thanks " + tierStr + " (+" + getGraciousThanksBonusPct() + "% TY Gems)";
            updated = true;
        }

        if (updated) {
            ConfigManager.save();
            LOGGER.info("[HPKarma] Auto-detected perk upgrade: {}", detectedPerk);
            showHudNotification("§aPerk Auto-Synced: §e" + detectedPerk);
            return true;
        }
        return false;
    }

    private static final Pattern PATTERN_TIER =
            Pattern.compile("(?i)(?:current\\s+tier:\\s*([ivxlcdm0-9]+)|tier:?\\s*([ivxlcdm0-9]+)|\\[(\\d+)/\\d+\\])");
    private static final Pattern PATTERN_ARROW_BOOST =
            Pattern.compile("\\+(\\d+)%\\s*(?:➔|->|➜|→)");
    private static final Pattern PATTERN_LEVEL =
            Pattern.compile("(?i)level:?\\s*(\\d+)");

    public static int extractPerkLevelFromItem(String text, int maxLevel, int[] percentages) {
        if (text == null || text.isEmpty()) return -1;

        // 1. Check for "[X/Y]" format e.g. "Current Tier: I [1/5]" -> 1, "[0/5]" -> 0
        Matcher mTier = PATTERN_TIER.matcher(text);
        if (mTier.find()) {
            if (mTier.group(3) != null) {
                try {
                    int val = Integer.parseInt(mTier.group(3));
                    if (val >= 0 && val <= maxLevel) return val;
                } catch (Exception ignored) {}
            }
            String tierToken = (mTier.group(1) != null) ? mTier.group(1) : mTier.group(2);
            if (tierToken != null) {
                String tt = tierToken.trim().toLowerCase();
                if (tt.equals("0") || tt.equals("none") || tt.equals("locked") || tt.equals("unlocked")) {
                    return 0;
                }
                int val = parseRomanOrInt(tierToken);
                if (val >= 0 && val <= maxLevel) return val;
            }
        }

        // 2. Check for percentage before upgrade arrow e.g. "+12% ➔ +24%" -> 12%, "+0% ➔ +12%" -> 0
        Matcher mArrow = PATTERN_ARROW_BOOST.matcher(text);
        if (mArrow.find()) {
            try {
                int currentPct = Integer.parseInt(mArrow.group(1));
                if (currentPct == 0) return 0;
                if (percentages != null) {
                    for (int i = 0; i < percentages.length; i++) {
                        if (percentages[i] == currentPct) return i + 1;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Check for standard "Level: X" or "Level X"
        Matcher mLevel = PATTERN_LEVEL.matcher(text);
        if (mLevel.find()) {
            try {
                int val = Integer.parseInt(mLevel.group(1));
                if (val >= 0 && val <= maxLevel) return val;
            } catch (Exception ignored) {}
        }

        // 4. Check for max level / last tier percentage if no arrow present
        if (percentages != null && percentages.length > 0) {
            int maxPct = percentages[percentages.length - 1];
            if (text.contains("+" + maxPct + "%") || text.toLowerCase().contains("max level") || text.toLowerCase().contains("max tier")) {
                return percentages.length;
            }
        }

        // 5. Check if explicitly unpurchased / tier 0
        String lower = text.toLowerCase();
        if (lower.contains("current tier: none") || lower.contains("current tier: 0") ||
            lower.contains("[0/") || lower.contains("tier: none") || lower.contains("tier: 0") ||
            lower.contains("not purchased") || lower.contains("not unlocked")) {
            return 0;
        }

        return -1;
    }

    private static int tickCounter = 0;
    private static long lastScanTime = 0;

    /**
     * Automatic GUI scanner: Inspects open chest/perk menus every ~500ms.
     * Automatically reads perk levels from slots and syncs them without user intervention.
     */
    public static void onClientTick(net.minecraft.class_310 client) {
        if (client == null) return;
        tickCounter++;
        if (tickCounter % 10 != 0) return; // 10 ticks = 500ms

        try {
            if (!ChatHandler.isHallowPrisonConnected()) return;
            if (!(client.field_1755 instanceof net.minecraft.class_465)) return;

            net.minecraft.class_465<?> handledScreen = (net.minecraft.class_465<?>) client.field_1755;
            net.minecraft.class_1703 handler = handledScreen.method_17577();
            if (handler == null || handler.field_7761 == null) return;

            long now = System.currentTimeMillis();
            if (now - lastScanTime < 1000) return; // At most once per second

            boolean anyChanged = false;

            for (net.minecraft.class_1735 slot : handler.field_7761) {
                if (slot == null) continue;
                net.minecraft.class_1799 stack = slot.method_7677();
                if (stack == null || stack.method_7960()) continue;

                StringBuilder sb = new StringBuilder();
                if (stack.method_7964() != null) {
                    sb.append(stack.method_7964().getString()).append("\n");
                }

                try {
                    java.util.List<net.minecraft.class_2561> lines = stack.method_7950(
                            net.minecraft.class_1792.class_9635.field_51353,
                            client.field_1724,
                            net.minecraft.class_1836.field_41070
                    );
                    if (lines != null) {
                        for (net.minecraft.class_2561 line : lines) {
                            if (line != null) sb.append(line.getString()).append("\n");
                        }
                    }
                } catch (Throwable ignored) {}

                if (stack.method_57353() != null) {
                    sb.append(stack.method_57353().toString()).append("\n");
                }

                String combined = sb.toString();
                String lower = combined.toLowerCase();

                if (lower.contains("good manners") || lower.contains("good_manners")) {
                    int lvl = extractPerkLevelFromItem(combined, 5, new int[]{12, 24, 36, 48, 60});
                    if (lvl >= 0 && lvl != goodMannersLevel) {
                        setGoodMannersLevel(lvl);
                        anyChanged = true;
                    }
                } else if (lower.contains("first ballot") || lower.contains("first_ballot")) {
                    int lvl = extractPerkLevelFromItem(combined, 5, new int[]{10, 20, 30, 40, 50});
                    if (lvl >= 0 && lvl != firstBallotLevel) {
                        setFirstBallotLevel(lvl);
                        anyChanged = true;
                    }
                } else if (lower.contains("rallying cry") || lower.contains("rallying_cry")) {
                    int lvl = extractPerkLevelFromItem(combined, 4, new int[]{20, 40, 60, 80});
                    if (lvl >= 0 && lvl != rallyingCryLevel) {
                        setRallyingCryLevel(lvl);
                        anyChanged = true;
                    }
                } else if (lower.contains("gracious thanks") || lower.contains("gracious_thanks")) {
                    int lvl = extractPerkLevelFromItem(combined, 2, new int[]{25, 50});
                    if (lvl >= 0 && lvl != graciousThanksLevel) {
                        setGraciousThanksLevel(lvl);
                        anyChanged = true;
                    }
                }
            }

            if (anyChanged) {
                lastScanTime = now;
                ConfigManager.save();
                LOGGER.info("[HPKarma] Auto-synced perks from GUI menu: GoodManners={}, FirstBallot={}, RallyingCry={}, GraciousThanks={}",
                        goodMannersLevel, firstBallotLevel, rallyingCryLevel, graciousThanksLevel);
                showHudNotification("§aPerks Auto-Synced: §eGM " + goodMannersLevel + ", FB " + firstBallotLevel + ", RC " + rallyingCryLevel);
            }
        } catch (Throwable ignored) {
            // Defensive: Never throw from tick listener
        }
    }

    public static void showHudNotification(String message) {
        try {
            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client != null && ChatHandler.hudNotification) {
                client.execute(() -> {
                    try {
                        if (client.field_1724 != null) {
                            String hud = "§8[§5§lH§6§lP§e§lKarma \uE17E§8] " + message;
                            client.field_1724.method_7353(net.minecraft.class_2561.method_30163(hud), true);
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }
}
