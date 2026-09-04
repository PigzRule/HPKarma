package com.pigzrule.hpkarma;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher, String rootCommand) {
        dispatcher.register(
            ClientCommandManager.literal(rootCommand)
                .executes(context -> {
                    sendStatus(context.getSource());
                    return 1;
                })
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        sendHelp(context.getSource());
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("toggle")
                    .executes(context -> {
                        ChatHandler.enabled = !ChatHandler.enabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Master switch: " + (ChatHandler.enabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("gg")
                    .executes(context -> {
                        ChatHandler.ggEnabled = !ChatHandler.ggEnabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Auto-GG: " + (ChatHandler.ggEnabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("welcome")
                    .executes(context -> {
                        ChatHandler.welcomeEnabled = !ChatHandler.welcomeEnabled;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Auto-Welcome: " + (ChatHandler.welcomeEnabled ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        sendStatus(context.getSource());
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("wavecooldown")
                    .executes(context -> {
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Wave lockout duration: §e" + (ChatHandler.waveCooldownMs / 1000) + "s"
                        ));
                        return 1;
                    })
                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(15, 180))
                        .executes(context -> {
                            int sec = IntegerArgumentType.getInteger(context, "seconds");
                            ChatHandler.waveCooldownMs = sec * 1000;
                            ConfigManager.save();
                            context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                                PREFIX + "§7Wave lockout duration set to §e" + sec + "s"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("cooldown")
                    .executes(context -> {
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Safety throttle: §e" + (ChatHandler.globalCooldownMs / 1000) + "s§7, Isolated trigger: §e" + (ChatHandler.triggerCooldownMs / 1000) + "s"
                        ));
                        return 1;
                    })
                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(4, 60))
                        .executes(context -> {
                            int sec = IntegerArgumentType.getInteger(context, "seconds");
                            ChatHandler.triggerCooldownMs = sec * 1000;
                            ChatHandler.globalCooldownMs = Math.max(2500, (sec - 2) * 1000);
                            ConfigManager.save();
                            context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                                PREFIX + "§7Safety throttle set to §e" + sec + "s"
                            ));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("serverlock")
                    .executes(context -> {
                        ChatHandler.serverLock = !ChatHandler.serverLock;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Server Lock (Hallow Prison only): " + (ChatHandler.serverLock ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("randomize")
                    .executes(context -> {
                        ChatHandler.randomizePhrases = !ChatHandler.randomizePhrases;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Phrase Randomization: " + (ChatHandler.randomizePhrases ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("rand")
                    .executes(context -> {
                        ChatHandler.randomizePhrases = !ChatHandler.randomizePhrases;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Phrase Randomization: " + (ChatHandler.randomizePhrases ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("hud")
                    .executes(context -> {
                        ChatHandler.hudNotification = !ChatHandler.hudNotification;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Actionbar HUD feedback: " + (ChatHandler.hudNotification ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("focus")
                    .executes(context -> {
                        ChatHandler.pauseWhenUnfocused = !ChatHandler.pauseWhenUnfocused;
                        ConfigManager.save();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Pause when tabbed out: " + (ChatHandler.pauseWhenUnfocused ? "§a✔" : "§c✖")
                        ));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("stats")
                    .executes(context -> {
                        int total = ChatHandler.SESSION_GGS.get() + ChatHandler.SESSION_WELCOMES.get();
                        context.getSource().sendFeedback(net.minecraft.class_2561.method_30163(
                            PREFIX + "§7Session: §e" + ChatHandler.SESSION_GGS.get() + " §7GGs, §e" + ChatHandler.SESSION_WELCOMES.get() + " §7Welcomes §8(~§a" + (total * 10) + " §7Karma§8)"
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
     * In Minecraft 1.21 Intermediary:
     * - class_315.method_42556() is getChatWidth() (returns SimpleOption<Double>)
     * - class_338.method_1806(double) calculates the width in pixels (d * 280.0 + 40.0)
     */
    public static int getChatWidth() {
        try {
            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client != null && client.field_1690 != null) {
                Object opt = client.field_1690.method_42556().method_41753();
                if (opt instanceof Double) {
                    return net.minecraft.class_338.method_1806((Double) opt);
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
    public static String getExactSpacing(net.minecraft.class_327 tr, int pixels) {
        if (pixels <= 0) return "";

        boolean supportsMicroSpacing = (tr != null && tr.method_1727("\uF00C") == 1);

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
        int spaceWidth = (tr != null) ? Math.max(1, tr.method_1727(" ")) : 4;
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
            net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
            if (client != null && client.field_1772 != null) {
                int chatWidth = getChatWidth();
                if (chatWidth > 30) {
                    int targetWidth = chatWidth - 8;
                    int dashWidth = client.field_1772.method_1727("-");
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
                        sb.append(getExactSpacing(client.field_1772, remainder));
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
    public static String centerText(net.minecraft.class_327 tr, int chatWidth, String text) {
        if (tr == null || chatWidth <= 30) return " " + text;
        int textWidth = tr.method_1727(text);
        int leftMargin = (chatWidth - textWidth) / 2;
        if (leftMargin <= 0) return text;
        return getExactSpacing(tr, leftMargin) + text;
    }

    public static net.minecraft.class_5250 makeButton(String text, String command, String hoverTooltip) {
        net.minecraft.class_5250 component = net.minecraft.class_2561.method_43470(text);
        net.minecraft.class_2583 style = net.minecraft.class_2583.field_24360
            .method_10958(new net.minecraft.class_2558.class_10609(command))
            .method_10949(new net.minecraft.class_2568.class_10613(net.minecraft.class_2561.method_43470(hoverTooltip)));
        return component.method_10862(style);
    }

    public static net.minecraft.class_5250 makeSuggestButton(String text, String command, String hoverTooltip) {
        net.minecraft.class_5250 component = net.minecraft.class_2561.method_43470(text);
        net.minecraft.class_2583 style = net.minecraft.class_2583.field_24360
            .method_10958(new net.minecraft.class_2558.class_10610(command))
            .method_10949(new net.minecraft.class_2568.class_10613(net.minecraft.class_2561.method_43470(hoverTooltip)));
        return component.method_10862(style);
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
        net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
        net.minecraft.class_327 tr = (client != null) ? client.field_1772 : null;
        int chatWidth = getChatWidth();
        String divider = getDynamicDivider();

        source.sendFeedback(net.minecraft.class_2561.method_30163(divider));
        String title = centerText(tr, chatWidth, "§5§lH§6§lP§e§lKarma \uE17E §8| §6Command Guide");
        source.sendFeedback(net.minecraft.class_2561.method_30163(title));
        source.sendFeedback(net.minecraft.class_2561.method_30163(""));

        String[][] cmds = new String[][] {
            { " §e/hpk toggle", "Toggle responder on/off" },
            { " §e/hpk gg", "Toggle Rebirth & Karma GG" },
            { " §e/hpk welcome", "Toggle welcome for new joins" },
            { " §e/hpk serverlock", "Toggle Hallow Prison only lock" },
            { " §e/hpk randomize", "Toggle phrase variations" },
            { " §e/hpk hud", "Toggle action-bar toast" },
            { " §e/hpk focus", "Toggle pause when tabbed out" },
            { " §e/hpk wavecooldown <sec>", "Wave lockout (15-180s)" },
            { " §e/hpk cooldown <sec>", "Safety spacing (4-60s)" },
            { " §e/hpk stats", "Show session stats" },
            { " §e/hpk status", "Open interactive status overview" }
        };

        int maxCmdWidth = 0;
        if (tr != null) {
            for (String[] c : cmds) {
                int w = tr.method_1727(stripColor(c[0]));
                if (w > maxCmdWidth) maxCmdWidth = w;
            }
        } else {
            maxCmdWidth = 145;
        }

        // Single-pixel column target anchor for the '»' symbol
        int targetCmd = maxCmdWidth + 8;

        for (String[] c : cmds) {
            int curCmdWidth = (tr != null) ? tr.method_1727(stripColor(c[0])) : 90;
            int needed = targetCmd - curCmdWidth;
            net.minecraft.class_5250 line = makeSuggestButton(c[0], c[0].trim() + " ", "§eClick to paste command into chat");
            line.method_10852(net.minecraft.class_2561.method_30163(getExactSpacing(tr, needed) + "§8» §7" + c[1]));
            source.sendFeedback(line);
        }

        source.sendFeedback(net.minecraft.class_2561.method_30163(""));
        source.sendFeedback(net.minecraft.class_2561.method_30163(" §8» §7Server karma balance: §e/karma§7 (Server command)"));
        source.sendFeedback(net.minecraft.class_2561.method_30163(divider));
    }

    private void sendStatus(FabricClientCommandSource source) {
        net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
        net.minecraft.class_327 tr = (client != null) ? client.field_1772 : null;
        int chatWidth = getChatWidth();
        String divider = getDynamicDivider();

        source.sendFeedback(net.minecraft.class_2561.method_30163(divider));
        String title = centerText(tr, chatWidth, "§5§lH§6§lP§e§lKarma \uE17E §8| §6Status & Overview");
        source.sendFeedback(net.minecraft.class_2561.method_30163(title));
        source.sendFeedback(net.minecraft.class_2561.method_30163(""));

        StatusRow[] rows = new StatusRow[] {
            new StatusRow(" §6\u270F §7Master Switch:",        ChatHandler.enabled ? "§a✔" : "§c✖", "", "/hpk toggle", "§eClick to toggle Master Switch", false),
            new StatusRow(" §6\u270F §7Rebirth GG:",           ChatHandler.ggEnabled ? "§a✔" : "§c✖", "§8(§bCyan§8)", "/hpk gg", "§eClick to toggle Auto-GG", false),
            new StatusRow(" §6\u270F §7Milestone GG:",         ChatHandler.ggEnabled ? "§a✔" : "§c✖", "§8(§6Orange§8)", "/hpk gg", "§eClick to toggle Auto-GG", false),
            new StatusRow(" §6\u270F §7New Player Welcome:",    ChatHandler.welcomeEnabled ? "§a✔" : "§c✖", "§8(§aGreen§8)", "/hpk welcome", "§eClick to toggle Auto-Welcome", false),
            new StatusRow(" §6\u270F §7Hallow Prison Lock:",   ChatHandler.serverLock ? "§a✔" : "§c✖", "§8(§7Lock to HP§8)", "/hpk serverlock", "§eClick to toggle server lock", false),
            new StatusRow(" §6\u270F §7Phrase Variation:",     ChatHandler.randomizePhrases ? "§a✔" : "§c✖", "§8(§7Anti-detection§8)", "/hpk randomize", "§eClick to toggle phrase randomization", false),
            new StatusRow(" §6\u270F §7HUD Notification:",     ChatHandler.hudNotification ? "§a✔" : "§c✖", "§8(§7Actionbar§8)", "/hpk hud", "§eClick to toggle HUD notices", false),
            new StatusRow(" §6\u270F §7Pause Tabbed Out:",     ChatHandler.pauseWhenUnfocused ? "§a✔" : "§c✖", "§8(§7Focus check§8)", "/hpk focus", "§eClick to toggle pause when tabbed out", false),
            new StatusRow(" §6\u270F §7Wave Lockout:",         "§e" + (ChatHandler.waveCooldownMs / 1000) + "s", "§8(§7Lockout§8)", "/hpk wavecooldown ", "§eClick to set wave lockout", true),
            new StatusRow(" §6\u270F §7Safety Throttle:",      "§e" + (ChatHandler.globalCooldownMs / 1000) + "s", "§8(§7Throttle§8)", "/hpk cooldown ", "§eClick to set safety throttle", true)
        };

        int maxLabelWidth = 0;
        int maxValueWidth = 0;
        if (tr != null) {
            for (StatusRow r : rows) {
                int lw = tr.method_1727(stripColor(r.label));
                if (lw > maxLabelWidth) maxLabelWidth = lw;
                int vw = tr.method_1727(stripColor(r.value));
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
            int curLabelWidth = (tr != null) ? tr.method_1727(stripColor(r.label)) : 90;
            int needed1 = col1Target - curLabelWidth;

            net.minecraft.class_5250 line = net.minecraft.class_2561.method_43470(r.label + getExactSpacing(tr, needed1));

            net.minecraft.class_5250 btn = r.isSuggest ?
                makeSuggestButton(r.value, r.command, r.tooltip) :
                makeButton(r.value, r.command, r.tooltip);
            line.method_10852(btn);

            if (!r.comment.isEmpty()) {
                int curValueWidth = (tr != null) ? tr.method_1727(stripColor(r.value)) : 15;
                int currentTotalWidth = col1Target + curValueWidth;
                int needed2 = col2Target - currentTotalWidth;
                line.method_10852(net.minecraft.class_2561.method_30163(getExactSpacing(tr, needed2) + r.comment));
            }

            source.sendFeedback(line);
        }

        int totalResponses = ChatHandler.SESSION_GGS.get() + ChatHandler.SESSION_WELCOMES.get();
        source.sendFeedback(net.minecraft.class_2561.method_30163(""));
        source.sendFeedback(net.minecraft.class_2561.method_30163(
            " §6⚡ §7Session Stats: §e" + ChatHandler.SESSION_GGS.get() + " §7GGs, §e" + ChatHandler.SESSION_WELCOMES.get() + " §7Welcomes §8(~§a" + (totalResponses * 10) + " §7Karma§8)"
        ));
        source.sendFeedback(net.minecraft.class_2561.method_30163(divider));
    }
}
