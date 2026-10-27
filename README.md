# HPKarma

HPKarma is a client-side Fabric mod for Hallow Prison on Minecraft 26.3 and newer. It sends configured `gg` and `welcome` replies and estimates Karma from detected Vote Perks.

## Features

- Detects Hallow Prison system announcements and sends delayed `gg` or `welcome` replies.
- Reads Good Manners, First Ballot, and Rallying Cry from Vote Perk menus and upgrade messages, then saves the detected levels.
- Estimates 25 base Karma per GG or welcome, adjusted by Good Manners. Vote and other Karma notifications use the amounts reported by the server.
- Provides `/hpk` status and settings commands, session statistics, HUD notices, and sound feedback.
- Includes cooldowns, self-event suppression, private-message filtering, and container/death safeguards.

## Requirements

- Minecraft 26.3+
- Fabric Loader 0.19.5+
- Java 25+
- Fabric API modules included in your Minecraft profile

## Build

Use a Minecraft 26.3 profile that has launched at least once, plus the matching Modrinth metadata and Java 25 runtime. From PowerShell:

```powershell
./build.ps1 -ProfilePath "$env:APPDATA/ModrinthApp/profiles/Fabulously Optimized (1)" -MetaPath "$env:APPDATA/ModrinthApp/meta" -JdkBin "C:/path/to/java-25/bin"
```

The script compiles the source and writes `build/libs/hp-karma-1.0.0-mc26.3.jar` and its SHA-256 checksum. It does not install the mod into a profile.

## Install

Copy `hp-karma-1.0.0-mc26.3.jar` into the Minecraft profile's `mods` folder.

## Karma perks

Good Manners increases GG and welcome Karma from 25 to as much as 40 per response. First Ballot and Rallying Cry increase Vote Karma. The AFK tree currently has no Karma bonuses. HPKarma scans perk menus when they are open and persists the detected levels in `config/hp-karma.json`.

The server does not provide perk levels in its join message, so a fresh config cannot infer them from connecting alone. Open the Vote Perks menu once to sync them. The source reference for the tree data is in `docs/perk-tree-reference.json`.

## License

MIT. See [LICENSE](LICENSE).
