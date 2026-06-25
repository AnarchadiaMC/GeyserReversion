# GeyserReversion
- Inspired by https://github.com/bundabrg/GeyserReversion, entirely different codebase tho uhhh
- The "library" used for translating stuff is a fork of [Ouranos](https://github.com/Blackjack200/Ouranos) aka https://github.com/oryxel1/Ouranos 
- Currently, support: Bedrock 1.21.130-1.21.132 (protocol 898) down to 1.12.0, depending on the installed Geyser build and available Ouranos translators.
- Built against Geyser API 2.9.5. On newer Geyser builds that no longer advertise the bridge protocol, the extension falls back to its newest local bridge codec and registers compatibility mappings from the active Geyser protocol.
- This will flag anticheats and stuff, it that ain't obvious enough already.
