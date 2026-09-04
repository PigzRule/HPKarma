# HPKarma

A lightweight Fabric mod for Hallow Prison that automatically responds to karma triggers (`gg` and `welcome`).

## Features

- **Human-like delays**: Random 1.8s–3.2s delay for `gg` and 2.4s–4.5s for `welcome`.
- **Anti-spam**: 45-second lockout per wave so it doesn't spam when chat floods.
- **Smart queue**: If multiple events trigger at once, it queues them with a natural pause between messages instead of sending them together.
- **Ignores private chat**: Never triggers on whispers, `/msg`, `/r`, or your own messages.
- **Pixel-perfect menus**: Chat menus (`/hpk help`, `/hpk status`) are aligned down to the pixel using the server's custom font glyphs.

## Commands

- `/hpk` or `/hpk status` — Show status and active settings
- `/hpk toggle` — Master on/off switch
- `/hpk gg` — Toggle auto-GG (rebirths & milestones)
- `/hpk welcome` — Toggle auto-welcome (new players)
- `/hpk wavecooldown <sec>` — Set wave cooldown (default: 45s)
- `/hpk cooldown <sec>` — Set minimum spacing between messages (default: 2.5s)
- `/hpk help` — Show command list
