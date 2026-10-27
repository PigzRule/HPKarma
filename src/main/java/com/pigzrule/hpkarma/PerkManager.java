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

    // Accept both legacy and newer server wording, with or without the SUCCESS/You prefix.
    private static final Pattern PERK_UPGRADE_PATTERN =
            Pattern.compile("(?i)(?:success!\\s*)?(?:you\\s+)?upgraded\\s+([A-Za-z\\s]+?)\\s+to\\s+([IVXLCDM0-9]+)\\b");

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
    private static final Pattern PATTERN_CURRENT_BOOST =
            Pattern.compile("(?i)(?:current|currently|active|owned)[^\\n]{0,48}?\\+(\\d+)%");
    private static final Pattern PATTERN_ANY_PERCENT = Pattern.compile("\\+(\\d+)%");
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

        // 2b. Some 26.x tooltips show the owned/current bonus without an upgrade arrow.
        Matcher mCurrent = PATTERN_CURRENT_BOOST.matcher(text);
        if (mCurrent.find()) {
            int level = levelForPercentage(mCurrent.group(1), percentages);
            if (level >= 0 && level <= maxLevel) return level;
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

        // 4b. Handle a tooltip that contains one unambiguous tier percentage and no label.
        if (percentages != null) {
            int candidateLevel = -1;
            boolean ambiguous = false;
            Matcher mAny = PATTERN_ANY_PERCENT.matcher(text);
            while (mAny.find()) {
                int level = levelForPercentage(mAny.group(1), percentages);
                if (level < 0) continue;
                if (candidateLevel >= 0 && candidateLevel != level) {
                    ambiguous = true;
                    break;
                }
                candidateLevel = level;
            }
            if (!ambiguous && candidateLevel >= 0 && candidateLevel <= maxLevel) return candidateLevel;
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

    private static int levelForPercentage(String token, int[] percentages) {
        if (token == null || percentages == null) return -1;
        try {
            int value = Integer.parseInt(token);
            if (value == 0) return 0;
            for (int i = 0; i < percentages.length; i++) {
                if (percentages[i] == value) return i + 1;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static int tickCounter = 0;
    private static long lastScanTime = 0;

    /**
     * Automatic GUI scanner: Inspects open chest/perk menus every ~500ms.
     * Automatically reads perk levels from slots and syncs them without user intervention.
     */
    public static void onClientTick(net.minecraft.client.Minecraft client) {
        if (client == null) return;
        tickCounter++;
        if (tickCounter % 10 != 0) return; // 10 ticks = 500ms

        try {
            if (!ChatHandler.isHallowPrisonConnected()) return;
            if (client.gui == null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;

            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handledScreen =
                    (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen();
            net.minecraft.world.inventory.AbstractContainerMenu handler = handledScreen.getMenu();
            if (handler == null || handler.slots == null) return;

            long now = System.currentTimeMillis();
            if (now - lastScanTime < 1000) return; // At most once per second
            lastScanTime = now;

            boolean anyChanged = false;

            for (net.minecraft.world.inventory.Slot slot : handler.slots) {
                if (slot == null) continue;
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) continue;

                StringBuilder sb = new StringBuilder();
                if (stack.getHoverName() != null) {
                    sb.append(stack.getHoverName().getString()).append("\n");
                }

                try {
                    java.util.List<net.minecraft.network.chat.Component> lines = stack.getTooltipLines(
                            net.minecraft.world.item.Item.TooltipContext.EMPTY,
                            client.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL
                    );
                    if (lines != null) {
                        for (net.minecraft.network.chat.Component line : lines) {
                            if (line != null) sb.append(line.getString()).append("\n");
                        }
                    }
                } catch (Throwable ignored) {}

                if (stack.getComponents() != null) {
                    sb.append(stack.getComponents().toString()).append("\n");
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
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && ChatHandler.hudNotification) {
                client.execute(() -> {
                    try {
                        if (client.player != null) {
                            String hud = "§8[§5§lH§6§lP§e§lKarma \uE17E§8] " + message;
                            client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(hud));
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }
}
