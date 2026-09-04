# HPKarma

A lightweight Fabric mod for Hallow Prison (Minecraft 1.21.11) that automatically responds to karma triggers (`gg` and `welcome`).

## Features

- **Human-like delays**: Random 1.8s–3.2s delay for `gg` and 2.4s–4.5s for `welcome`.
- **Exact Karma Compatibility**: Sends the exact lowercase `"gg"` and `"welcome"` strings strictly required by Hallow Prison to turn cyan and credit +25 Karma.
- **Server lock**: Automatically restricts auto-responses strictly to Hallow Prison so you never accidentally chat on other servers or singleplayer.
- **Clickable menus**: `/hpk status` and `/hpk help` have clickable toggles and command paste buttons right in chat.
- **Active draft protection**: If you're actively typing in chat, it sends responses silently in the background without closing your chat box or deleting your draft.
- **Anti-spam**: 45-second lockout per wave with a 9-second queue expiration so you never send awkward late messages.
- **Ignores private chat & player baiting**: Triggers strictly on server announcements; ignores whispers, `/msg`, `/r`, player chat bait, and your own messages.
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
- `/hpk stats` — View session responses and estimated karma earned (+25 Karma/event)
- `/hpk wavecooldown <sec>` — Set wave cooldown (default: 45s)
- `/hpk cooldown <sec>` — Set minimum spacing between messages (default: 2.5s)
- `/hpk help` — Show clickable command list

## Installation

Drop `hp-karma-1.0.1.jar` into your `.minecraft/mods` folder. Requires Fabric Loader (0.16+) and Fabric API on Minecraft 1.21.11.
