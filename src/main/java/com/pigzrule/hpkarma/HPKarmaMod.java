package com.pigzrule.hpkarma;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HPKarmaMod implements ClientModInitializer {
    public static final String MOD_ID = "hp-karma";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Clean brand prefix: Royal Purple H, Orange P, Yellow Karma with official Karma coin
    private static final String PREFIX = "§8[§5§lH§6§lP§e§lKarma \uE17E§8] §r";

    @Override
    public void onInitializeClient() {
        LOGGER.info("[HPKarma] Initializing HPKarma for Hallow Prison (Multi-Type Stacking & Wave Protection)...");

        // Load persistent configuration from config/hp-karma.json
        ConfigManager.load();

        // 1. Game / System Message Listener
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                ChatHandler.handleIncoming(message, null, overlay);
            } catch (Throwable t) {
                LOGGER.error("[HPKarma] Error in GAME message listener", t);
            }
        });

        // 2. Player Chat Listener
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            try {
                String senderName = ChatHandler.getProfileName(sender);
                ChatHandler.handleIncoming(message, senderName);
            } catch (Throwable t) {
                LOGGER.error("[HPKarma] Error in CHAT message listener", t);
            }
        });

        // 3. Automatic Perk GUI Scanner (scans container menu when opened)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PerkManager::onClientTick);

        // 3. Client Commands (Notice: /karma is NOT registered here so the server's /karma command is untouched)
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                try {
                    registerCommands(dispatcher, "hpkarma");
                    registerCommands(dispatcher, "hpk");
                    registerCommands(dispatcher, "autogg");
                } catch (Throwable t) {
                    LOGGER.error("[HPKarma] Failed to register client commands", t);
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[HPKarma] Failed to bind command callback", t);
        }

        LOGGER.info("[HPKarma] Ready! Commands: /hpkarma, /hpk, /autogg (Server /karma preserved)");
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, String rootCommand) {
        dispatcher.register(
            ClientCommands.literal(rootCommand)
                .executes(context -> {
                    sendStatus(context.getSource());
                    return 1;
                })
                .then(ClientCommands.literal("help")
                    .executes(context -> {
                        sendHelp(context.getSource());
                        return 1;
                    })
                )
                .then(ClientCommands.literal("toggle")
                    .executes(context -> {
                        ChatHandler.enabled = !ChatHandler.enabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Master switch: " + (ChatHandler.enabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("gg")
                    .executes(context -> {
                        ChatHandler.ggEnabled = !ChatHandler.ggEnabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Auto-GG: " + (ChatHandler.ggEnabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("welcome")
                    .executes(context -> {
                        ChatHandler.welcomeEnabled = !ChatHandler.welcomeEnabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Auto-Welcome: " + (ChatHandler.welcomeEnabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("status")
                    .executes(context -> {
                        sendStatus(context.getSource());
                        return 1;
                    })
                )
                .then(ClientCommands.literal("wavecooldown")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Wave lockout duration: §e" + (ChatHandler.waveCooldownMs / 1000) + "s"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(0, 180))
                        .executes(context -> {
                            int sec = IntegerArgumentType.getInteger(context, "seconds");
                            ChatHandler.waveCooldownMs = sec * 1000;
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Wave lockout duration set to §e" + sec + "s"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("cooldown")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Safety throttle: §e" + (ChatHandler.globalCooldownMs / 1000) + "s§7, Isolated trigger: §e" + (ChatHandler.triggerCooldownMs / 1000) + "s"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(4, 60))
                        .executes(context -> {
                            int sec = IntegerArgumentType.getInteger(context, "seconds");
                            ChatHandler.triggerCooldownMs = sec * 1000;
                            ChatHandler.globalCooldownMs = Math.max(2500, (sec - 2) * 1000);
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Safety throttle set to §e" + sec + "s"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("selflockout")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Self-event lockout: §e" + (ChatHandler.selfEventLockoutMs / 1000) + "s"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(0, 60))
                        .executes(context -> {
                            int sec = IntegerArgumentType.getInteger(context, "seconds");
                            ChatHandler.selfEventLockoutMs = sec * 1000;
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Self-event lockout set to §e" + sec + "s"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("serverlock")
                    .executes(context -> {
                        ChatHandler.serverLock = !ChatHandler.serverLock;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Server Lock (Hallow Prison only): " + (ChatHandler.serverLock ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("hud")
                    .executes(context -> {
                        ChatHandler.hudNotification = !ChatHandler.hudNotification;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Actionbar HUD feedback: " + (ChatHandler.hudNotification ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("sound")
                    .executes(context -> {
                        ChatHandler.soundNotification = !ChatHandler.soundNotification;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Audio chime feedback: " + (ChatHandler.soundNotification ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("focus")
                    .executes(context -> {
                        ChatHandler.pauseWhenUnfocused = !ChatHandler.pauseWhenUnfocused;
                        ConfigManager.save();
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Pause when tabbed out: " + (ChatHandler.pauseWhenUnfocused ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("karmapergg")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Estimated Karma per GG/Welcome: §e+" + ChatHandler.karmaPerGg + " Karma"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("amount", IntegerArgumentType.integer(0, 500))
                        .executes(context -> {
                            int amt = IntegerArgumentType.getInteger(context, "amount");
                            ChatHandler.karmaPerGg = amt;
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Estimated Karma per GG/Welcome set to §e+" + amt + " Karma"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("goodmanners")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Good Manners Perk: §eLvl " + PerkManager.goodMannersLevel +
                            " §8(+§a" + PerkManager.getGoodMannersBonusPct(PerkManager.goodMannersLevel) + "% §7-> §a+" + ChatHandler.karmaPerGg + " Karma§8)"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 5))
                        .executes(context -> {
                            int lvl = IntegerArgumentType.getInteger(context, "level");
                            PerkManager.setGoodMannersLevel(lvl);
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Good Manners set to §eLvl " + lvl + " §8(+§a" + PerkManager.getGoodMannersBonusPct(lvl) + "% §7-> §a+" + ChatHandler.karmaPerGg + " Karma/GG§8)"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("gm")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Good Manners Perk: §eLvl " + PerkManager.goodMannersLevel +
                            " §8(+§a" + PerkManager.getGoodMannersBonusPct(PerkManager.goodMannersLevel) + "% §7-> §a+" + ChatHandler.karmaPerGg + " Karma§8)"
                        ));
                        return 1;
                    })
                    .then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 5))
                        .executes(context -> {
                            int lvl = IntegerArgumentType.getInteger(context, "level");
                            PerkManager.setGoodMannersLevel(lvl);
                            ConfigManager.save();
                            context.getSource().sendFeedback(Component.literal(
                                PREFIX + "§7Good Manners set to §eLvl " + lvl + " §8(+§a" + PerkManager.getGoodMannersBonusPct(lvl) + "% §7-> §a+" + ChatHandler.karmaPerGg + " Karma/GG§8)"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("stats")
                    .executes(context -> {
                        int ggs = ChatHandler.SESSION_GGS.get();
                        int welcomes = ChatHandler.SESSION_WELCOMES.get();
                        int votes = ChatHandler.SESSION_VOTES.get();
                        int totalKarma = ChatHandler.SESSION_GG_KARMA.get() + ChatHandler.SESSION_VOTE_KARMA.get();

                        int gmBoost = PerkManager.getGoodMannersBonusPct();
                        int voteBoost = PerkManager.getTotalVoteBonusPct();

                        String gmTag = " §8(+" + gmBoost + "%)";
                        String votePart = (votes > 0) ? (", §e" + votes + " §7Votes §8(+" + voteBoost + "%)") : "";

                        context.getSource().sendFeedback(Component.literal(
                            PREFIX + "§7Session: §e" + ggs + " §7GGs" + gmTag + "§7, §e" + welcomes + " §7Welcomes" + gmTag + votePart + " §8(§a" + totalKarma + " §7Karma§8)"
                        ));
                        return 1;
                    })
                )
        );
    }

    private static final java.util.regex.Pattern COLOR_PATTERN = java.util.regex.Pattern.compile("(?i)§[0-9a-fk-or]");

    /**
     * Strips section formatting codes (§a, §b, §l, §r, etc.) to get pure visible characters.
     */
    public static String stripColor(String str) {
        if (str == null) return "";
        return COLOR_PATTERN.matcher(str).replaceAll("");
    }

    /**
     * Gets the active chat width in scaled pixels from Minecraft's ChatHud logic.
     */
    public static int getChatWidth() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.options != null) {
                Double opt = client.options.chatWidth().get();
                if (opt != null) {
                    return ChatComponent.getWidth(opt);
                }
            }
        } catch (Throwable ignored) {}
        return 320;
    }

    /**
     * Builds an exact sequence of spacing characters that offsets text by an exact pixel amount.
     * Uses Hallow Prison's native font advances:
     * \uF010 = 16px
     * \uF00F = 8px
     * \uF00E = 4px
     * \uF00D = 2px
     * \uF00C = 1px
     * Falls back to high-precision spaces if micro-glyphs are not active.
     */
    public static String getExactSpacing(Font tr, int pixels) {
        if (pixels <= 0) return "";

        boolean supportsMicroSpacing = (tr != null && tr.width("\uF00C") == 1);

        if (supportsMicroSpacing) {
            StringBuilder sb = new StringBuilder();
            while (pixels >= 16) {
                sb.append('\uF010');
                pixels -= 16;
            }
            if (pixels >= 8) {
                sb.append('\uF00F');
                pixels -= 8;
            }
            if (pixels >= 4) {
                sb.append('\uF00E');
                pixels -= 4;
            }
            if (pixels >= 2) {
                sb.append('\uF00D');
                pixels -= 2;
            }
            if (pixels >= 1) {
                sb.append('\uF00C');
                pixels -= 1;
            }
            return sb.toString();
        }

        // High-precision fallback for standard fonts:
        int spaceWidth = (tr != null) ? Math.max(1, tr.width(" ")) : 4;
        int spaces = (pixels + (spaceWidth / 2)) / spaceWidth;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Dynamically calculates a horizontal divider line that spans the full width of the chat box.
     */
    public static String getDynamicDivider() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.font != null) {
                int chatWidth = getChatWidth();
                if (chatWidth > 30) {
                    int targetWidth = chatWidth - 8;
                    int dashWidth = client.font.width("-");
                    if (dashWidth <= 0) dashWidth = 4;

                    int count = targetWidth / dashWidth;
                    if (count > 120) count = 120;
                    if (count < 10) count = 10;

                    StringBuilder sb = new StringBuilder("§8§m");
                    for (int i = 0; i < count; i++) {
                        sb.append('-');
                    }
                    sb.append("§r");

                    int remainder = targetWidth - (count * dashWidth);
                    if (remainder > 0) {
                        sb.append(getExactSpacing(client.font, remainder));
                    }

                    return sb.toString();
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[HPKarma] Failed to compute dynamic chat divider", t);
        }
        return "§8§m----------------------------------------------------------------------§r";
    }

    /**
     * Centers a formatted text line within the active chat window with single-pixel precision.
     */
    public static String centerText(Font tr, int chatWidth, String text) {
        if (tr == null || chatWidth <= 30) return " " + text;
        int textWidth = tr.width(text);
        int leftMargin = (chatWidth - textWidth) / 2;
        if (leftMargin <= 0) return text;
        return getExactSpacing(tr, leftMargin) + text;
    }

    public static MutableComponent makeButton(String text, String command, String hoverTooltip) {
        String cleanCommand = stripColor(command);
        MutableComponent component = Component.literal(text);
        Style style = Style.EMPTY
            .withClickEvent(new ClickEvent.RunCommand(cleanCommand))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverTooltip)));
        return component.setStyle(style);
    }

    public static MutableComponent makeSuggestButton(String text, String command, String hoverTooltip) {
        String cleanCommand = stripColor(command);
        MutableComponent component = Component.literal(text);
        Style style = Style.EMPTY
            .withClickEvent(new ClickEvent.SuggestCommand(cleanCommand))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverTooltip)));
        return component.setStyle(style);
    }

    private static class StatusRow {
        final String label;
        final String value;
        final String comment;
        final String command;
        final String tooltip;
        final boolean isSuggest;

        StatusRow(String label, String value, String comment, String command, String tooltip, boolean isSuggest) {
            this.label = label;
            this.value = value;
            this.comment = comment;
            this.command = command;
            this.tooltip = tooltip;
            this.isSuggest = isSuggest;
        }
    }

    private void sendHelp(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        Font tr = (client != null) ? client.font : null;
        int chatWidth = getChatWidth();
        String divider = getDynamicDivider();

        source.sendFeedback(Component.literal(divider));
        String title = centerText(tr, chatWidth, "§5§lH§6§lP§e§lKarma \uE17E §8| §6Command Guide");
        source.sendFeedback(Component.literal(title));
        source.sendFeedback(Component.literal(""));

        String[][] cmds = new String[][] {
            { " §e/hpk toggle", "Toggle responder on/off" },
            { " §e/hpk gg", "Toggle Rebirth & Karma GG" },
            { " §e/hpk welcome", "Toggle welcome for new joins" },
            { " §e/hpk serverlock", "Toggle Hallow Prison only lock" },
            { " §e/hpk hud", "Toggle action-bar toast" },
            { " §e/hpk sound", "Toggle audio chime feedback" },
            { " §e/hpk focus", "Toggle pause when tabbed out" },
            { " §e/hpk wavecooldown <sec>", "Wave lockout (0-180s)" },
            { " §e/hpk cooldown <sec>", "Safety spacing (4-60s)" },
            { " §e/hpk selflockout <sec>", "Self-rebirth lockout (0-60s)" },
            { " §e/hpk stats", "Show session stats" },
            { " §e/hpk status", "Open interactive status overview" }
        };

        int maxCmdWidth = 0;
        if (tr != null) {
            for (String[] c : cmds) {
                int w = tr.width(stripColor(c[0]));
                if (w > maxCmdWidth) maxCmdWidth = w;
            }
        } else {
            maxCmdWidth = 145;
        }

        // Single-pixel column target anchor for the '»' symbol
        int targetCmd = maxCmdWidth + 8;

        for (String[] c : cmds) {
            int curCmdWidth = (tr != null) ? tr.width(stripColor(c[0])) : 90;
            int needed = targetCmd - curCmdWidth;
            String suggestCmd = stripColor(c[0]).trim();
            if (suggestCmd.endsWith(" <sec>")) {
                suggestCmd = suggestCmd.substring(0, suggestCmd.length() - 6) + " ";
            }
            MutableComponent line = makeSuggestButton(c[0], suggestCmd, "§eClick to paste command into chat");
            line.append(Component.literal(getExactSpacing(tr, needed) + "§8» §7" + c[1]));
            source.sendFeedback(line);
        }

        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal(" §8» §7Server karma balance: §e/karma§7 (Server command)"));
        source.sendFeedback(Component.literal(divider));
    }

    private void sendStatus(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        Font tr = (client != null) ? client.font : null;
        int chatWidth = getChatWidth();
        String divider = getDynamicDivider();

        source.sendFeedback(Component.literal(divider));
        String title = centerText(tr, chatWidth, "§5§lH§6§lP§e§lKarma \uE17E §8| §6Status & Overview");
        source.sendFeedback(Component.literal(title));
        source.sendFeedback(Component.literal(""));

        StatusRow[] rows = new StatusRow[] {
            new StatusRow(" §6\u270F §7Master Switch:",        ChatHandler.enabled ? "§a✔" : "§c✖", "", "/hpk toggle", "§eClick to toggle Master Switch", false),
            new StatusRow(" §6\u270F §7Rebirth GG:",           ChatHandler.ggEnabled ? "§a✔" : "§c✖", "§8(§bMulti§7-§ccolored§8)", "/hpk gg", "§eClick to toggle Auto-GG", false),
            new StatusRow(" §6\u270F §7Milestone GG:",         ChatHandler.ggEnabled ? "§a✔" : "§c✖", "§8(§6Orange§8)", "/hpk gg", "§eClick to toggle Auto-GG", false),
            new StatusRow(" §6\u270F §7New Player Welcome:",    ChatHandler.welcomeEnabled ? "§a✔" : "§c✖", "§8(§aGreen§8)", "/hpk welcome", "§eClick to toggle Auto-Welcome", false),
            new StatusRow(" §6\u270F §7Hallow Prison Lock:",   ChatHandler.serverLock ? "§a✔" : "§c✖", "§8(§7Lock to HP§8)", "/hpk serverlock", "§eClick to toggle server lock", false),
            new StatusRow(" §6\u270F §7HUD Notification:",     ChatHandler.hudNotification ? "§a✔" : "§c✖", "§8(§7Actionbar§8)", "/hpk hud", "§eClick to toggle HUD notices", false),
            new StatusRow(" §6\u270F §7Audio Chime:",          ChatHandler.soundNotification ? "§a✔" : "§c✖", "§8(§7Orb chime§8)", "/hpk sound", "§eClick to toggle audio chime", false),
            new StatusRow(" §6\u270F §7Pause Tabbed Out:",     ChatHandler.pauseWhenUnfocused ? "§a✔" : "§c✖", "§8(§7Focus check§8)", "/hpk focus", "§eClick to toggle pause when tabbed out", false),
            new StatusRow(" §6\u270F §7Wave Lockout:",         "§e" + (ChatHandler.waveCooldownMs / 1000) + "s", "§8(§7Lockout§8)", "/hpk wavecooldown ", "§eClick to set wave lockout", true),
            new StatusRow(" §6\u270F §7Safety Throttle:",      "§e" + (ChatHandler.globalCooldownMs / 1000) + "s", "§8(§7Throttle§8)", "/hpk cooldown ", "§eClick to set safety throttle", true),
            new StatusRow(" §6\u270F §7Self Lockout:",         "§e" + (ChatHandler.selfEventLockoutMs / 1000) + "s", "§8(§7Self-event§8)", "/hpk selflockout ", "§eClick to set self-event lockout", true)
        };

        int maxLabelWidth = 0;
        int maxValueWidth = 0;
        if (tr != null) {
            for (StatusRow r : rows) {
                int lw = tr.width(stripColor(r.label));
                if (lw > maxLabelWidth) maxLabelWidth = lw;
                int vw = tr.width(stripColor(r.value));
                if (vw > maxValueWidth) maxValueWidth = vw;
            }
        } else {
            maxLabelWidth = 135;
            maxValueWidth = 20;
        }

        // Single-pixel column target anchors
        int col1Target = maxLabelWidth + 10;
        int col2Target = col1Target + maxValueWidth + 14;

        for (StatusRow r : rows) {
            int curLabelWidth = (tr != null) ? tr.width(stripColor(r.label)) : 90;
            int needed1 = col1Target - curLabelWidth;

            MutableComponent line = Component.literal(r.label + getExactSpacing(tr, needed1));

            MutableComponent btn = r.isSuggest ?
                makeSuggestButton(r.value, r.command, r.tooltip) :
                makeButton(r.value, r.command, r.tooltip);
            line.append(btn);

            if (!r.comment.isEmpty()) {
                int curValueWidth = (tr != null) ? tr.width(stripColor(r.value)) : 15;
                int currentTotalWidth = col1Target + curValueWidth;
                int needed2 = col2Target - currentTotalWidth;
                line.append(Component.literal(getExactSpacing(tr, needed2) + r.comment));
            }

            source.sendFeedback(line);
        }

        int ggs = ChatHandler.SESSION_GGS.get();
        int welcomes = ChatHandler.SESSION_WELCOMES.get();
        int votes = ChatHandler.SESSION_VOTES.get();
        int totalKarma = ChatHandler.SESSION_GG_KARMA.get() + ChatHandler.SESSION_VOTE_KARMA.get();

        int gmBoost = PerkManager.getGoodMannersBonusPct();
        int voteBoost = PerkManager.getTotalVoteBonusPct();

        String gmTag = " §8(+" + gmBoost + "%)";
        String votePart = (votes > 0) ? (", §e" + votes + " §7Votes §8(+" + voteBoost + "%)") : "";

        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal(
            " §6⚡ §7Session Stats: §e" + ggs + " §7GGs" + gmTag + "§7, §e" + welcomes + " §7Welcomes" + gmTag + votePart + " §8(§a" + totalKarma + " §7Karma§8)"
        ));
        source.sendFeedback(Component.literal(divider));
    }
}
