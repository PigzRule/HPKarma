# HPKarma

A lightweight Fabric mod for Hallow Prison (Minecraft 1.21.11) that automatically responds to karma triggers (`gg` and `welcome`).

## Features

- **Human-like delays**: Random 1.8s–3.2s delay for `gg` and 2.4s–4.5s for `welcome`.
- **Exact Karma Compatibility**: Sends the exact lowercase `"gg"` and `"welcome"` strings strictly required by Hallow Prison to turn blue and credit +25 Karma.
- **Server lock**: Automatically restricts auto-responses strictly to Hallow Prison so you never accidentally chat on other servers or singleplayer.
- **Clickable menus**: `/hpk status` and `/hpk help` feature interactive toggles and one-click command paste buttons (with zero leaked formatting codes and ready-to-type arguments).
- **Tabbed-out & Dynamic FPS compatibility**: Dispatches packets directly in the background when unfocused, completely bypassing 0-FPS render freeze when tabbed out.
- **Active draft & inventory protection**: Sends responses silently without closing your chat box or clearing typed drafts. Opening your survival inventory (`E` key) to unlock your cursor won't cancel responses.
- **Anti-spam**: 45-second lockout per wave with a 9-second queue expiration so you never send awkward late messages.
- **Self-event & private alert filtering**: Strictly ignores your own rebirth announcements, level 100 reminders (`INFO! You can now rebirth!`), station teleports, player chat bait, whispers, and economy notices.
- **Customizable self-rebirth lockout**: Includes a brief 4-second pause after your own rebirth (configurable via `/hpk selflockout`) so congratulations to other players who rebirth shortly after you are never missed.
- **Container & death safety**: Suppresses auto-responses while inside chests, shops, or container GUIs, and while dead.
- **Fair play**: Ignores webstore purchases to respect server rules against AFK gem farming.
- **Audio & HUD feedback**: Optional subtle experience chime (`/hpk sound`) and action-bar notice (`/hpk hud`).

## Commands

- `/hpk` or `/hpk status` — Interactive status menu (click `[✔]` or `[✖]` to toggle)
- `/hpk toggle` — Master on/off switch
- `/hpk gg` — Toggle auto-GG (rebirths & milestones)
- `/hpk welcome` — Toggle auto-welcome (new players)
- `/hpk serverlock` — Toggle Hallow Prison only server restriction
- `/hpk hud` — Toggle subtle action-bar toast notifications
- `/hpk sound` — Toggle subtle audio chime feedback
- `/hpk focus` — Toggle auto-pause when Minecraft is tabbed out
- `/hpk stats` — View session responses and exact karma earned (+25 Karma/event)
- `/hpk wavecooldown <sec>` — Set wave lockout duration (default: 45s)
- `/hpk cooldown <sec>` — Set safety spacing between messages (default: 2.5s)
- `/hpk selflockout <sec>` — Set brief pause after self-rebirth announcements (default: 4s, 0 to disable)
- `/hpk help` — Show clickable command guide

> **Note:** The server's native `/karma` command is completely untouched so you can still check your server karma balance at any time.

## Installation

Drop `hp-karma-1.0.0.jar` into your `.minecraft/mods` folder. Requires Fabric Loader (0.16+) and Fabric API on Minecraft 1.21.11.
