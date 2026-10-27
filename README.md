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

## Install

Copy `hp-karma-1.0.0-mc26.3.jar` into the Minecraft profile's `mods` folder.

## License

MIT. See [LICENSE](LICENSE).
