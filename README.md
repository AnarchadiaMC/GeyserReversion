# GeyserReversion
- Inspired by https://github.com/bundabrg/GeyserReversion, entirely different codebase tho uhhh
- The "library" used for translating stuff is a fork of [Ouranos](https://github.com/Blackjack200/Ouranos) aka https://github.com/oryxel1/Ouranos 
- Currently supports Bedrock 26.45 (protocol 2169) through Geyser and translates legacy Bedrock clients down to 1.12.0, depending on the available Ouranos translators.
- Built against Geyser API 2.11.2 and the Floodgate API bundled by that Geyser build. When Geyser no longer advertises the local bridge protocol, the extension falls back to its newest local bridge codec and registers compatibility mappings from the active Geyser protocol.
- This will flag anticheats and stuff, it that ain't obvious enough already.
