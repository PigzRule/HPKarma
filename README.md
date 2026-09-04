# HPKarma

A lightweight Fabric mod for Hallow Prison (Minecraft 1.21.11) that automatically responds to karma triggers (`gg` and `welcome`).

## Features

- **Human-like delays**: Random 1.8s–3.2s delay for `gg` and 2.4s–4.5s for `welcome`.
- **Anti-detection**: Randomizes capitalization and phrasing (`gg`, `GG`, `ggs`, `Gg`, `gg!`) so you don't look like a bot.
- **Server lock**: Automatically restricts auto-responses strictly to Hallow Prison so you never accidentally chat on other servers or singleplayer.
- **Clickable menus**: `/hpk status` and `/hpk help` have clickable toggles and command paste buttons right in chat.
- **Active draft protection**: If you're actively typing in chat, it sends responses silently in the background without closing your chat box or deleting your draft.
- **Anti-spam**: 45-second lockout per wave with a 9-second queue expiration so you never send awkward late messages.
- **Ignores private chat**: Never triggers on whispers, `/msg`, `/r`, or your own messages.
- **Fair play**: Ignores webstore purchases to respect server rules against AFK gem farming.

## Commands

- `/hpk` or `/hpk status` — Interactive status menu (click `[✔]` or `[✖]` to toggle)
- `/hpk toggle` — Master on/off switch
- `/hpk gg` — Toggle auto-GG (rebirths & milestones)
- `/hpk welcome` — Toggle auto-welcome (new players)
- `/hpk serverlock` — Toggle Hallow Prison only server restriction
- `/hpk randomize` — Toggle phrase variations
- `/hpk hud` — Toggle subtle action-bar toast notifications
- `/hpk focus` — Toggle auto-pause when Minecraft is tabbed out
- `/hpk stats` — View session responses and estimated karma earned
- `/hpk wavecooldown <sec>` — Set wave cooldown (default: 45s)
- `/hpk cooldown <sec>` — Set minimum spacing between messages (default: 2.5s)
- `/hpk help` — Show clickable command list

## Installation

Drop `hp-karma-1.0.0.jar` into your `.minecraft/mods` folder. Requires Fabric Loader (0.16+) and Fabric API on Minecraft 1.21.11.
