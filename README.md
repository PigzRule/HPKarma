# HPKarma 🪙

A client-side Fabric mod for **Minecraft 1.21.11**, made specifically for playing on **Hallow Prison**.

---

### Why I built this

If you play on Hallow Prison, you already know the routine: someone rebirths, hits a big milestone, or joins for the first time, and the server broadcasts a prompt to say `gg` or `welcome` to earn Karma.

Most generic AutoGG mods out there are pretty rough around the edges—they fire off responses in 50 milliseconds (making it painfully obvious you're running a macro), spam repeated messages when chat floods, or randomly reply to private messages.

I wanted something that feels natural, plays nice with server rules, and doesn't look like an annoying robot in chat.

---

### Cool things under the hood

- **Natural human reaction time**  
  Nobody types `gg` the instant a packet reaches their PC. HPKarma adds a randomized 1.8s–3.2s delay for GGs and 2.4s–4.5s for welcomes. If a rebirth and a welcome happen at the exact same time, it queues them up sequentially with a realistic typing pause in between instead of blurting them out simultaneously.

- **Wave lockouts (no spamming)**  
  When 50 players start spamming `gg` after an announcement, the mod sends your response once and then locks that category for 45 seconds. It also waits for chat to quiet down before ever considering another wave.

- **Private messages stay private**  
  Whispers, `/msg`, `/tell`, `/r`, and the server's envelope icons (`✉`, `YOU →`, `→ YOU`) are completely isolated. The mod will never accidentally leak a response into your DMs or get caught in an echo loop from your own chat.

- **Single-pixel UI alignment**  
  Ragged, uneven text menus in chat drive me nuts. I dug through Hallow Prison's resource pack and found their custom micro-glyph spacing characters (`\uF00C` through `\uF010`, which offset text by 1, 2, 4, 8, and 16 pixels). I built a small font-spacing calculator into the mod so all the `/hpk help` and `/hpk status` tables line up down to the exact pixel, regardless of your GUI scale or chat width. It even pulls the server's official Karma Coin glyph (`\uE17E`) right into the header.

- **Doesn't break `/karma`**  
  The server already has a `/karma` command for checking your balance. To keep things clean and conflict-free, this mod uses `/hpk` and `/hpkarma`.

---

### Fair play & server rules

Server owner MarcusSlover understandably wants to prevent AFK gem farming through automated store `ty` responses. HPKarma respects server rules and fair play:

- **Store purchases are completely ignored**: It does not respond to webstore purchases, package announcements, or store waves. No AFK gem farming.
- **Purely for community karma**: Focuses 100% on legitimate community karma triggers—Rebirth GGs, Milestone GGs, and welcoming new joins.

---

### In-Game Commands

| Command | What it does |
| :--- | :--- |
| `/hpk` or `/hpk status` | Opens the pixel-aligned status dashboard |
| `/hpk toggle` | Quick master switch to turn the mod on or off |
| `/hpk gg` | Toggle Rebirth (`§b` Cyan) & Milestone (`§6` Orange) GGs |
| `/hpk welcome` | Toggle New Player Welcome (`§a` Green) |
| `/hpk wavecooldown <sec>` | Change wave lockout length (default: 45s) |
| `/hpk cooldown <sec>` | Adjust minimum spacing between messages (default: 2.5s) |
| `/hpk help` | View the in-game command guide |

---

### Building it

I set up a lightweight PowerShell build script that compiles directly against the local JDK and Fabric libraries (no heavy Gradle daemons running in the background):

```powershell
.\build.ps1
```
*(This compiles `src/` and drops `hp-karma-1.0.0.jar` right into your active profile's `mods` folder.)*

---

### Requirements
- Minecraft **1.21.11** with **Fabric Loader** (tested with Fabulously Optimized)
- Java 21
